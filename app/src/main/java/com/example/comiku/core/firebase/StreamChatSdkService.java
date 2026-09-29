package com.example.comiku.core.firebase;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import com.example.comiku.data.model.ChatMessageData;
import com.example.comiku.data.model.StreamChatTokenResponse;
import com.example.comiku.data.repository.StreamChatRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import io.getstream.chat.android.client.ChatClient;
import io.getstream.chat.android.client.channel.ChannelClient;
import io.getstream.chat.android.models.Attachment;
import io.getstream.chat.android.models.Message;
import io.getstream.chat.android.models.UploadedFile;
import io.getstream.chat.android.models.User;
import io.getstream.chat.android.state.plugin.config.StatePluginConfig;
import io.getstream.chat.android.state.plugin.factory.StreamStatePluginFactory;
import io.getstream.result.Error;
import io.getstream.result.Result;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

// Conecta StreamChat
public class StreamChatSdkService {
    private static final String TAG = "StreamChatSdkService";
    private static final String STREAM_CHANNEL_TYPE = "messaging";
    private static StreamChatSdkService instancia;

    private final Context context;
    private final FirebaseAuth auth;
    private final StreamChatRepository repositorio;
    private final Handler handlerPrincipal;
    private ChatClient cliente;
    private String apiKeyActual;
    private boolean estaConectando = false;
    private final List<StreamClientCallback> callbacksPendientes = new ArrayList<>();
    private static final int MAX_INTENTOS_CONEXION = 3;
    private static final long RETRASO_REINTENTO_CONEXION_MS = 700L;
    private static final int MAX_INTENTOS_CARGA_MENSAJES = 3;
    private static final long RETRASO_REINTENTO_CARGA_MS = 500L;

    public interface StreamSendCallback {
        void onExito(ChatMessageData mensaje);
        void onError(String error);
    }

    public interface StreamClientCallback {
        void onExito(ChatClient cliente);
        void onError(String error);
    }

    public interface StreamMessagesCallback {
        void onExito(List<ChatMessageData> mensajes);
        void onError(String error);
    }

    private StreamChatSdkService(Context context) {
        this.context = context.getApplicationContext();
        this.auth = FirebaseAuth.getInstance();
        this.repositorio = StreamChatRepository.obtenerInstancia(context);
        this.handlerPrincipal = new Handler(Looper.getMainLooper());
    }

    // Obtiene la instancia compartida del servicio.
    public static synchronized StreamChatSdkService obtenerInstancia(Context context) {
        if (instancia == null) {
            instancia = new StreamChatSdkService(context);
        }
        return instancia;
    }

    // Asegura que Stream quede conectado para el usuario actual.
    public void asegurarCliente(StreamClientCallback callback) {
        FirebaseUser usuarioFirebase = auth.getCurrentUser();
        if (usuarioFirebase == null) {
            callback.onError("Usuario no autenticado");
            return;
        }

        if (cliente != null && cliente.getCurrentUser() != null
                && usuarioFirebase.getUid().equals(cliente.getCurrentUser().getId())
                && tienePluginEstado()) {
            callback.onExito(cliente);
            return;
        }

        repositorio.obtenerTokenStreamChat(new StreamChatRepository.TokenCallback() {
            @Override
            public void onExito(StreamChatTokenResponse response) {
                if (response.getApiKey() == null || response.getApiKey().trim().isEmpty()) {
                    callback.onError("No se pudo obtener la apiKey de StreamChat");
                    return;
                }
                iniciarConexionConReintento(response.getApiKey(), response.getToken(), usuarioFirebase, callback);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Conecta Stream usando credenciales ya obtenidas
    public void conectarConCredenciales(String apiKey, String token, StreamClientCallback callback) {
        FirebaseUser usuarioFirebase = auth.getCurrentUser();
        if (usuarioFirebase == null) {
            callback.onError("Usuario no autenticado");
            return;
        }
        if (TextUtils.isEmpty(apiKey) || TextUtils.isEmpty(token)) {
            callback.onError("Credenciales de StreamChat incompletas");
            return;
        }

        if (cliente != null && cliente.getCurrentUser() != null
                && usuarioFirebase.getUid().equals(cliente.getCurrentUser().getId())) {
            callback.onExito(cliente);
            return;
        }

        iniciarConexionConReintento(apiKey, token, usuarioFirebase, callback);
    }

    // Devuelve el cliente de StreamChat que esta conectado en esta sesion.
    public ChatClient obtenerClienteActual() {
        return cliente;
    }

    // Inicia una conexion unica con reintentos cortos si la red tarda en responder.
    private void iniciarConexionConReintento(String apiKey, String token, FirebaseUser usuarioFirebase,
                                             StreamClientCallback callback) {
        if (TextUtils.isEmpty(apiKey) || TextUtils.isEmpty(token)) {
            callback.onError("Credenciales de StreamChat incompletas");
            return;
        }

        synchronized (this) {
            if (cliente != null && cliente.getCurrentUser() != null
                    && usuarioFirebase.getUid().equals(cliente.getCurrentUser().getId())
                    && tienePluginEstado()) {
                callback.onExito(cliente);
                return;
            }

            callbacksPendientes.add(callback);
            apiKeyActual = apiKey;
            if (estaConectando) {
                return;
            }
            estaConectando = true;
            cliente = construirCliente(apiKeyActual);
        }

        User usuarioStream = crearUsuarioStream(usuarioFirebase);
        intentarConexionUsuario(usuarioStream, token, 0);
    }

    // Reintenta la conexion de Stream sin mostrar un error inmediato por fallas transitorias.
    private void intentarConexionUsuario(User usuarioStream, String token, int intentoActual) {
        ChatClient clienteActual = cliente;
        if (clienteActual == null) {
            notificarFalloConexion("No se pudo crear el cliente de StreamChat");
            return;
        }

        clienteActual.connectUser(usuarioStream, token).enqueue(result -> {
            if (result.isSuccess()) {
                synchronized (this) {
                    estaConectando = false;
                }
                notificarConexionExitosa(clienteActual);
                return;
            }

            Error error = result.errorOrNull();
            String mensajeError = error != null ? error.getMessage() : "No se pudo conectar a StreamChat";
            if (intentoActual + 1 < MAX_INTENTOS_CONEXION && esErrorTransitorio(mensajeError)) {
                handlerPrincipal.postDelayed(() -> intentarConexionUsuario(usuarioStream, token, intentoActual + 1),
                        RETRASO_REINTENTO_CONEXION_MS);
                return;
            }

            synchronized (this) {
                estaConectando = false;
            }
            notificarFalloConexion(mensajeError);
        });
    }

    // Notifica exito a todos los callbacks pendientes.
    private void notificarConexionExitosa(ChatClient clienteConectado) {
        List<StreamClientCallback> callbacks;
        synchronized (this) {
            callbacks = new ArrayList<>(callbacksPendientes);
            callbacksPendientes.clear();
        }
        for (StreamClientCallback callback : callbacks) {
            callback.onExito(clienteConectado);
        }
    }

    // Notifica un error a todos los callbacks pendientes.
    private void notificarFalloConexion(String error) {
        List<StreamClientCallback> callbacks;
        synchronized (this) {
            callbacks = new ArrayList<>(callbacksPendientes);
            callbacksPendientes.clear();
            estaConectando = false;
            cliente = null;
        }
        for (StreamClientCallback callback : callbacks) {
            callback.onError(error);
        }
    }

    // Carga mensajes de Stream con un reintento corto para fallas transitorias de conexion.
    private void cargarMensajesConReintento(ChannelClient canal, String channelId, StreamMessagesCallback callback,
                                            int intentoActual) {
        canal.watch().enqueue(result -> {
            if (!result.isSuccess()) {
                Error error = result.errorOrNull();
                String mensajeError = error != null ? error.getMessage() : "No se pudo cargar mensajes desde StreamChat";
                if (intentoActual + 1 < MAX_INTENTOS_CARGA_MENSAJES && esErrorTransitorio(mensajeError)) {
                    handlerPrincipal.postDelayed(() ->
                                    cargarMensajesConReintento(canal, channelId, callback, intentoActual + 1),
                            RETRASO_REINTENTO_CARGA_MS);
                    return;
                }
                callback.onError(mensajeError);
                return;
            }

            io.getstream.chat.android.models.Channel canalStream = result.getOrNull();
            if (canalStream == null) {
                callback.onError("No se pudo leer el canal de StreamChat");
                return;
            }

            List<Message> mensajesStream = canalStream.getMessages();
            List<ChatMessageData> mensajes = convertirMensajesDesdeStream(channelId, mensajesStream);
            callback.onExito(mensajes);
        });
    }

    // Decide si el error parece transitorio y vale la pena reintentar.
    private boolean esErrorTransitorio(String error) {
        if (TextUtils.isEmpty(error)) {
            return false;
        }
        String mensaje = error.toLowerCase();
        return mensaje.contains("network")
                || mensaje.contains("connect")
                || mensaje.contains("timeout")
                || mensaje.contains("socket")
                || mensaje.contains("unavailable")
                || mensaje.contains("tempor");
    }

    // Crea el cliente de StreamChat con el plugin de estado habilitado.
    private ChatClient construirCliente(String apiKey) {
        return new ChatClient.Builder(apiKey, context)
                .withPlugins(new StreamStatePluginFactory(new StatePluginConfig(), context))
                .build();
    }

    // Revisa si el cliente actual ya tiene el plugin de estado necesario para la UI.
    private boolean tienePluginEstado() {
        if (cliente == null || cliente.getPluginFactories() == null) {
            return false;
        }
        for (int i = 0; i < cliente.getPluginFactories().size(); i++) {
            if (cliente.getPluginFactories().get(i) instanceof StreamStatePluginFactory) {
                return true;
            }
        }
        return false;
    }

    // Envía un mensaje de texto o con una imagen
    public void enviarMensaje(String channelId, String localMessageId, String texto, Uri adjuntoUri,
                              String attachmentType, String attachmentFileName, String attachmentMimeType,
                              StreamSendCallback callback) {
        asegurarCliente(new StreamClientCallback() {
            @Override
            public void onExito(ChatClient clienteStream) {
                ChannelClient canal = clienteStream.channel(STREAM_CHANNEL_TYPE, channelId);
                String textoLimpio = texto == null ? "" : texto.trim();

                if (adjuntoUri == null || TextUtils.isEmpty(attachmentType)) {
                    enviarTexto(canal, localMessageId, textoLimpio, callback);
                    return;
                }

                try {
                    File archivoTemporal = copiarUriAArchivoTemporal(adjuntoUri, attachmentFileName, attachmentMimeType);
                    if (archivoTemporal.length() <= 0L) {
                        callback.onError("El archivo adjunto esta vacio");
                        borrarArchivoTemporal(archivoTemporal);
                        return;
                    }
                    subirAdjuntoYEnviarMensaje(canal, archivoTemporal, textoLimpio, localMessageId,
                            attachmentType, attachmentFileName, attachmentMimeType, callback);
                } catch (IOException error) {
                    callback.onError("No se pudo preparar el archivo adjunto: " + error.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Carga mensajes desde StreamChat para mantener paridad con la version web
    public void cargarMensajesDesdeStream(String channelId, StreamMessagesCallback callback) {
        if (channelId == null || channelId.trim().isEmpty()) {
            callback.onError("Canal invalido");
            return;
        }

        asegurarCliente(new StreamClientCallback() {
            @Override
            public void onExito(ChatClient clienteStream) {
                cargarMensajesConReintento(clienteStream.channel(STREAM_CHANNEL_TYPE, channelId), channelId, callback, 0);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Construye un mensaje de texto y lo envía.
    private void enviarTexto(ChannelClient canal, String localMessageId, String texto, StreamSendCallback callback) {
        Message mensaje = new Message.Builder()
                .withId(localMessageId)
                .withText(texto)
                .build();

        canal.sendMessage(mensaje).enqueue(result -> procesarResultadoMensaje(result, localMessageId, texto, null, callback));
    }

    // Sube el archivo y luego envía el mensaje con el archivo adjunto.
    private void subirAdjuntoYEnviarMensaje(ChannelClient canal, File archivoTemporal, String texto,
                                            String localMessageId, String attachmentType, String attachmentFileName,
                                            String attachmentMimeType, StreamSendCallback callback) {
        boolean esImagen = "image".equals(attachmentType);
        io.getstream.result.call.Call<UploadedFile> call = esImagen
                ? canal.sendImage(archivoTemporal)
                : canal.sendFile(archivoTemporal);

        call.enqueue(result -> {
            try {
                if (!result.isSuccess()) {
                    Error error = result.errorOrNull();
                    callback.onError(error != null ? error.getMessage() : "No se pudo subir el archivo adjunto");
                    return;
                }

                UploadedFile archivoSubido = result.getOrNull();
                if (archivoSubido == null || archivoSubido.getFile() == null) {
                    callback.onError("No se obtuvo la URL del archivo adjunto");
                    return;
                }

                Attachment attachment = crearAttachment(archivoSubido, attachmentType, attachmentFileName, attachmentMimeType);
                List<Attachment> adjuntos = new ArrayList<>();
                adjuntos.add(attachment);

                Message mensaje = new Message.Builder()
                        .withId(localMessageId)
                        .withText(texto)
                        .withAttachments(adjuntos)
                        .build();

                canal.sendMessage(mensaje).enqueue(mensajeResult ->
                        procesarResultadoMensaje(mensajeResult, localMessageId, texto, archivoSubido, callback));
            } finally {
                borrarArchivoTemporal(archivoTemporal);
            }
        });
    }

    // Convierte la respuesta de StreamChat en un mensaje local
    private void procesarResultadoMensaje(Result<? extends Message> result, String localMessageId, String texto,
                                          UploadedFile archivoSubido, StreamSendCallback callback) {
        try {
            if (!result.isSuccess()) {
                Error error = result.errorOrNull();
                callback.onError(error != null ? error.getMessage() : "No se pudo enviar el mensaje");
                return;
            }

            Message mensajeStream = (Message) result.getOrNull();
            if (mensajeStream == null) {
                callback.onError("No se pudo leer la respuesta de StreamChat");
                return;
            }

            ChatMessageData mensaje = new ChatMessageData();
            mensaje.setId(localMessageId);
            mensaje.setStreamMessageId(mensajeStream.getId());
            mensaje.setChannelId(extraerChannelId(mensajeStream.getCid()));
            mensaje.setUserId(mensajeStream.getUser() != null ? mensajeStream.getUser().getId() : obtenerUsuarioActualId());
            mensaje.setText(texto == null ? "" : texto.trim());
            mensaje.setCreatedAt(mensajeStream.getCreatedAt() != null ? mensajeStream.getCreatedAt() : new Date());
            mensaje.setStatus("sent");
            mensaje.setReadBy(0);

            if (archivoSubido != null && !mensajeStream.getAttachments().isEmpty()) {
                Attachment attachment = mensajeStream.getAttachments().get(0);
                mensaje.setAttachmentType(normalizarTipoAdjunto(attachment));
                mensaje.setAttachmentUrl(extraerUrlAdjunto(attachment));
                mensaje.setAttachmentFileName(obtenerNombreAdjunto(attachment, archivoSubido));
            }

            callback.onExito(mensaje);
        } catch (Exception error) {
            callback.onError("No se pudo procesar el mensaje enviado");
        }
    }

    // Crea el adjunto que StreamChat usara al enviar el mensaje.
    private Attachment crearAttachment(UploadedFile archivoSubido, String attachmentType, String attachmentFileName,
                                       String attachmentMimeType) {
        Attachment.Builder builder = new Attachment.Builder()
                .withType(attachmentType)
                .withName(attachmentFileName)
                .withTitle(attachmentFileName)
                .withMimeType(attachmentMimeType);

        if ("image".equals(attachmentType)) {
            builder.withImageUrl(archivoSubido.getFile()).withThumbUrl(archivoSubido.getThumbUrl());
        } else {
            builder.withAssetUrl(archivoSubido.getFile());
        }

        return builder.build();
    }

    // Copia el contenido de un Uri a un archivo temporal.
    private File copiarUriAArchivoTemporal(Uri uri, String attachmentFileName, String attachmentMimeType) throws IOException {
        String nombreArchivo = obtenerNombreArchivo(uri, attachmentFileName, attachmentMimeType);
        String nombreSeguro = nombreArchivo.replaceAll("[\\\\/:*?\"<>|]", "_");
        File archivoTemporal = new File(
                context.getCacheDir(),
                "stream_upload_" + System.currentTimeMillis() + "_" + nombreSeguro
        );

        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null && "file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
            inputStream = new FileInputStream(new File(uri.getPath()));
        }
        if (inputStream == null) {
            throw new IOException("No se pudo abrir el archivo");
        }

        FileOutputStream outputStream = new FileOutputStream(archivoTemporal);
        byte[] buffer = new byte[8192];
        int longitud;

        try {
            while ((longitud = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, longitud);
            }
            outputStream.flush();
            return archivoTemporal;
        } finally {
            try {
                inputStream.close();
            } catch (IOException error) {
                Log.w(TAG, "No se pudo cerrar el input stream", error);
            }
            try {
                outputStream.close();
            } catch (IOException error) {
                Log.w(TAG, "No se pudo cerrar el output stream", error);
            }
        }
    }

    // Borra el archivo temporal cuando ya no se necesita.
    private void borrarArchivoTemporal(File archivoTemporal) {
        if (archivoTemporal != null && archivoTemporal.exists()) {
            archivoTemporal.delete();
        }
    }

    // Crea el usuario de StreamChat a partir del usuario de Firebase.
    private User crearUsuarioStream(FirebaseUser usuarioFirebase) {
        String nombre = usuarioFirebase.getDisplayName();
        if (TextUtils.isEmpty(nombre)) {
            nombre = usuarioFirebase.getEmail();
        }
        if (TextUtils.isEmpty(nombre)) {
            nombre = usuarioFirebase.getUid();
        }

        String imagen = usuarioFirebase.getPhotoUrl() != null ? usuarioFirebase.getPhotoUrl().toString() : "";

        return new User.Builder()
                .withId(usuarioFirebase.getUid())
                .withName(nombre)
                .withImage(imagen)
                .build();
    }

    // Obtiene el id del canal a partir del cid de StreamChat
    private String extraerChannelId(String cid) {
        if (cid == null) {
            return "";
        }
        int indice = cid.indexOf(':');
        if (indice < 0 || indice + 1 >= cid.length()) {
            return cid;
        }
        return cid.substring(indice + 1);
    }

    // Busca el nombre final del archivo para guardarlo en el mensaje.
    private String obtenerNombreArchivo(Uri uri, String attachmentFileName, String attachmentMimeType) {
        if (!TextUtils.isEmpty(attachmentFileName)) {
            return attachmentFileName;
        }

        String nombreDesdeUri = obtenerNombreDesdeContenido(uri);
        if (!TextUtils.isEmpty(nombreDesdeUri)) {
            return nombreDesdeUri;
        }

        String extension = obtenerExtensionDesdeMimeType(attachmentMimeType);
        if (!TextUtils.isEmpty(extension)) {
            return "adjunto_" + System.currentTimeMillis() + "." + extension;
        }

        return "adjunto_" + System.currentTimeMillis();
    }

    // Lee el nombre visible del archivo desde el ContentResolver.
    private String obtenerNombreDesdeContenido(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int indiceNombre = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (indiceNombre >= 0) {
                    return cursor.getString(indiceNombre);
                }
            }
        } catch (Exception error) {
            Log.w(TAG, "No se pudo leer el nombre del archivo", error);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }


    private String obtenerExtensionDesdeMimeType(String mimeType) {
        if (TextUtils.isEmpty(mimeType)) {
            return "";
        }
        int indice = mimeType.indexOf('/');
        if (indice < 0 || indice + 1 >= mimeType.length()) {
            return "";
        }
        return mimeType.substring(indice + 1);
    }

    // Obtiene un nombre seguro para mostrar el adjunto enviado.
    private String obtenerNombreAdjunto(Attachment attachment, UploadedFile archivoSubido) {
        if (attachment.getName() != null && !attachment.getName().trim().isEmpty()) {
            return attachment.getName();
        }
        if (attachment.getTitle() != null && !attachment.getTitle().trim().isEmpty()) {
            return attachment.getTitle();
        }
        if (archivoSubido != null && archivoSubido.getFile() != null) {
            return archivoSubido.getFile();
        }
        return "adjunto";
    }

    // Obtiene el id del usuario actual en Firebase.
    private String obtenerUsuarioActualId() {
        FirebaseUser usuarioFirebase = auth.getCurrentUser();
        return usuarioFirebase != null ? usuarioFirebase.getUid() : "";
    }

    // Convierte mensajes de StreamChat al modelo local.
    private List<ChatMessageData> convertirMensajesDesdeStream(String channelId, List<Message> mensajesStream) {
        List<ChatMessageData> mensajes = new ArrayList<>();
        String usuarioActualId = obtenerUsuarioActualId();
        if (mensajesStream == null) {
            return mensajes;
        }

        for (Message mensajeStream : mensajesStream) {
            try {
                if (mensajeStream == null || mensajeStream.getId() == null || mensajeStream.getId().trim().isEmpty()) {
                    continue;
                }

                ChatMessageData mensaje = new ChatMessageData();
                mensaje.setId(mensajeStream.getId());
                mensaje.setStreamMessageId(mensajeStream.getId());
                mensaje.setChannelId(channelId);
                mensaje.setUserId(mensajeStream.getUser() != null ? mensajeStream.getUser().getId() : "");
                mensaje.setText(mensajeStream.getText() == null ? "" : mensajeStream.getText());
                mensaje.setCreatedAt(mensajeStream.getCreatedAt() != null ? mensajeStream.getCreatedAt() : new Date());
                mensaje.setStatus("sent");
                mensaje.setReadBy(0);
                mensaje.setOwn(TextUtils.equals(mensaje.getUserId(), usuarioActualId));

                List<Attachment> adjuntos = mensajeStream.getAttachments();
                boolean adjuntoAplicado = false;
                if (adjuntos != null && !adjuntos.isEmpty()) {
                    Attachment adjuntoSeleccionado = seleccionarAdjuntoPrincipal(adjuntos, mensaje.getText());
                    if (adjuntoSeleccionado != null) {
                        adjuntoAplicado = aplicarAdjuntoStream(mensaje, adjuntoSeleccionado);
                    }
                }
                if (!adjuntoAplicado) {
                    aplicarAdjuntoDesdeMensajeExtraData(mensaje, mensajeStream);
                }
                if (esAdjuntoSinDatos(mensaje) && adjuntos != null && !adjuntos.isEmpty()) {
                    mensaje.setAttachmentType("file");
                    if (mensaje.getAttachmentFileName() == null || mensaje.getAttachmentFileName().trim().isEmpty()) {
                        mensaje.setAttachmentFileName("adjunto");
                    }
                }
                if ((mensaje.getAttachmentType() == null || mensaje.getAttachmentType().trim().isEmpty())
                        && esNombreAudio(mensaje.getText())) {
                    mensaje.setAttachmentType("audio");
                    mensaje.setAttachmentFileName(mensaje.getText().trim());
                    mensaje.setText("");
                }
                if ("audio".equals(mensaje.getAttachmentType())
                        && (mensaje.getAttachmentUrl() == null || mensaje.getAttachmentUrl().trim().isEmpty())) {
                    Log.d(TAG, "Audio sin url. messageId=" + mensaje.getId()
                            + " texto=" + mensajeStream.getText()
                            + " adjuntosSdk=" + String.valueOf(mensajeStream.getAttachments())
                            + " extraData=" + String.valueOf(mensajeStream.getExtraData()));
                }

                mensajes.add(mensaje);
            } catch (Exception error) {
                Log.w(TAG, "No se pudo mapear un mensaje de StreamChat", error);
            }
        }

        Collections.sort(mensajes, new Comparator<ChatMessageData>() {
            @Override
            public int compare(ChatMessageData a, ChatMessageData b) {
                long fechaA = a.getCreatedAt() != null ? a.getCreatedAt().getTime() : 0L;
                long fechaB = b.getCreatedAt() != null ? b.getCreatedAt().getTime() : 0L;
                return Long.compare(fechaB, fechaA);
            }
        });

        return mensajes;
    }

    // Normaliza el tipo de adjunto para compatibilidad entre Android y React.
    private String normalizarTipoAdjunto(Attachment attachment) {
        if (attachment == null) {
            return "";
        }

        String tipo = attachment.getType() == null ? "" : attachment.getType().trim().toLowerCase();
        String mime = attachment.getMimeType() == null ? "" : attachment.getMimeType().trim().toLowerCase();
        String nombre = attachment.getName() == null ? "" : attachment.getName().trim().toLowerCase();
        String titulo = attachment.getTitle() == null ? "" : attachment.getTitle().trim().toLowerCase();
        Map<String, Object> extraData = attachment.getExtraData();
        if (extraData != null) {
            String mimeExtra = obtenerValorExtra(extraData, "mime_type");
            if (mime.isEmpty() && !mimeExtra.isEmpty()) {
                mime = mimeExtra.toLowerCase();
            }
            if (nombre.isEmpty()) {
                nombre = obtenerValorExtra(extraData, "name").toLowerCase();
            }
            if (titulo.isEmpty()) {
                titulo = obtenerValorExtra(extraData, "title").toLowerCase();
            }
        }

        if ("image".equals(tipo)) {
            return "image";
        }
        if ("audio".equals(tipo)) {
            return "audio";
        }
        if (mime.startsWith("image/")) {
            return "image";
        }
        if (mime.startsWith("audio/")) {
            return "audio";
        }
        if (nombre.endsWith(".webm") || nombre.endsWith(".ogg") || nombre.endsWith(".m4a") || nombre.endsWith(".mp3") || nombre.endsWith(".wav")) {
            return "audio";
        }
        if (titulo.endsWith(".webm") || titulo.endsWith(".ogg") || titulo.endsWith(".m4a") || titulo.endsWith(".mp3") || titulo.endsWith(".wav")) {
            return "audio";
        }
        String assetUrl = attachment.getAssetUrl() == null ? "" : attachment.getAssetUrl().trim().toLowerCase();
        String imageUrl = attachment.getImageUrl() == null ? "" : attachment.getImageUrl().trim().toLowerCase();
        if (esRutaAudio(assetUrl) || esRutaAudio(imageUrl)) {
            return "audio";
        }
        return tipo;
    }

    // Copia al mensaje local los datos del archivo adjunto normalizado de StreamChat.
    private boolean aplicarAdjuntoStream(ChatMessageData mensaje, Attachment primerAdjunto) {
        if (mensaje == null || primerAdjunto == null) {
            return false;
        }
        String tipoNormalizado = normalizarTipoAdjunto(primerAdjunto);
        if (TextUtils.isEmpty(tipoNormalizado) && esNombreAudio(mensaje.getText())) {
            tipoNormalizado = "audio";
        }
        String urlAdjunto = extraerUrlAdjunto(primerAdjunto);

        String nombreAdjunto = primerAdjunto.getName();
        if (nombreAdjunto == null || nombreAdjunto.trim().isEmpty()) {
            nombreAdjunto = primerAdjunto.getTitle();
        }
        if ((nombreAdjunto == null || nombreAdjunto.trim().isEmpty()) && esNombreAudio(mensaje.getText())) {
            nombreAdjunto = mensaje.getText().trim();
        }

        mensaje.setAttachmentType(tipoNormalizado);
        mensaje.setAttachmentUrl(urlAdjunto);
        mensaje.setAttachmentFileName(nombreAdjunto);

        if ("audio".equals(tipoNormalizado) && esTextoSoloNombreAudio(mensaje.getText(), nombreAdjunto)) {
            mensaje.setText("");
        }
        return !TextUtils.isEmpty(tipoNormalizado) || !TextUtils.isEmpty(urlAdjunto) || !TextUtils.isEmpty(nombreAdjunto);
    }

    // Elige el archivo adjunto mas util para renderizar en Android.
    private Attachment seleccionarAdjuntoPrincipal(List<Attachment> adjuntos, String textoMensaje) {
        if (adjuntos == null || adjuntos.isEmpty()) {
            return null;
        }
        Attachment mejorAdjunto = null;
        int mejorPuntaje = -1;
        for (Attachment adjunto : adjuntos) {
            if (adjunto == null) {
                continue;
            }
            String tipo = normalizarTipoAdjunto(adjunto);
            String url = extraerUrlAdjunto(adjunto);
            String nombre = adjunto.getName();
            if (TextUtils.isEmpty(nombre)) {
                nombre = adjunto.getTitle();
            }
            int puntaje = 0;
            if ("audio".equals(tipo)) {
                puntaje += 4;
            } else if ("image".equals(tipo)) {
                puntaje += 3;
            } else if ("file".equals(tipo)) {
                puntaje += 2;
            }
            if (!TextUtils.isEmpty(url)) {
                puntaje += 3;
            }
            if (!TextUtils.isEmpty(nombre)) {
                puntaje += 1;
            }
            if (esRutaAudio(url) || esRutaAudio(nombre) || esNombreAudio(textoMensaje)) {
                puntaje += 3;
            }
            if (puntaje > mejorPuntaje) {
                mejorPuntaje = puntaje;
                mejorAdjunto = adjunto;
            }
        }
        return mejorAdjunto;
    }

    // Recupera archivos adjuntos cuando StreamChat los deja en extraData del mensaje.
    private void aplicarAdjuntoDesdeMensajeExtraData(ChatMessageData mensaje, Message mensajeStream) {
        if (mensaje == null || mensajeStream == null) {
            return;
        }
        Map<String, Object> extraData = mensajeStream.getExtraData();
        if (extraData == null) {
            return;
        }
        Object adjuntosRaw = extraData.get("attachments");
        if (!(adjuntosRaw instanceof List)) {
            return;
        }
        List<?> listaAdjuntos = (List<?>) adjuntosRaw;
        if (listaAdjuntos.isEmpty() || !(listaAdjuntos.get(0) instanceof Map)) {
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> primerAdjuntoRaw = (Map<String, Object>) listaAdjuntos.get(0);
        String tipo = obtenerValorExtra(primerAdjuntoRaw, "type").toLowerCase();
        String mime = obtenerValorExtra(primerAdjuntoRaw, "mime_type").toLowerCase();
        String nombre = obtenerValorExtra(primerAdjuntoRaw, "name");
        if (nombre.isEmpty()) {
            nombre = obtenerValorExtra(primerAdjuntoRaw, "title");
        }
        String url = obtenerValorExtra(primerAdjuntoRaw, "asset_url");
        if (url.isEmpty()) {
            url = obtenerValorExtra(primerAdjuntoRaw, "image_url");
        }
        if (url.isEmpty()) {
            url = obtenerValorExtra(primerAdjuntoRaw, "url");
        }

        if (tipo.isEmpty()) {
            if (mime.startsWith("audio/") || esRutaAudio(nombre) || esRutaAudio(url) || esNombreAudio(mensaje.getText())) {
                tipo = "audio";
            } else if (mime.startsWith("image/")) {
                tipo = "image";
            }
        }

        if (nombre.isEmpty() && esNombreAudio(mensaje.getText())) {
            nombre = mensaje.getText().trim();
        }

        mensaje.setAttachmentType(tipo);
        mensaje.setAttachmentUrl(url);
        mensaje.setAttachmentFileName(nombre);

        if ("audio".equals(tipo) && esTextoSoloNombreAudio(mensaje.getText(), nombre)) {
            mensaje.setText("");
        }
    }

    // Extrae la url util del adjunto desde campos principales y extras de StreamChat.
    private String extraerUrlAdjunto(Attachment attachment) {
        if (attachment == null) {
            return null;
        }
        if (!TextUtils.isEmpty(attachment.getImageUrl())) {
            return attachment.getImageUrl();
        }
        if (!TextUtils.isEmpty(attachment.getAssetUrl())) {
            return attachment.getAssetUrl();
        }

        Map<String, Object> extraData = attachment.getExtraData();
        if (extraData == null) {
            return null;
        }

        String[] claves = new String[]{"asset_url", "assetUrl", "image_url", "imageUrl", "url", "file", "thumb_url", "thumbUrl", "secure_url", "secureUrl", "link"};
        for (String clave : claves) {
            String valor = obtenerValorExtra(extraData, clave);
            if (!valor.isEmpty()) {
                return valor;
            }
        }

        Object archivoObjeto = extraData.get("file");
        if (archivoObjeto instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mapaArchivo = (Map<String, Object>) archivoObjeto;
            String urlArchivo = obtenerValorExtra(mapaArchivo, "url");
            if (!urlArchivo.isEmpty()) {
                return urlArchivo;
            }
            String urlSegura = obtenerValorExtra(mapaArchivo, "secure_url");
            if (!urlSegura.isEmpty()) {
                return urlSegura;
            }
        }
        return null;
    }

    // Obtiene un valor de extraData en formato String.
    private String obtenerValorExtra(Map<String, Object> extraData, String clave) {
        if (extraData == null || clave == null || clave.trim().isEmpty()) {
            return "";
        }
        Object valor = extraData.get(clave);
        return valor == null ? "" : String.valueOf(valor).trim();
    }

    // Indica si un texto parece nombre de archivo de audio.
    private boolean esNombreAudio(String valor) {
        if (valor == null) {
            return false;
        }
        String texto = valor.trim().toLowerCase();
        return esRutaAudio(texto);
    }

    // Detecta cuando el texto del mensaje solo repite el nombre del audio.
    private boolean esTextoSoloNombreAudio(String textoMensaje, String nombreAdjunto) {
        if (textoMensaje == null) {
            return false;
        }
        String texto = textoMensaje.trim();
        if (texto.isEmpty()) {
            return false;
        }
        if (nombreAdjunto != null && !nombreAdjunto.trim().isEmpty() && texto.equalsIgnoreCase(nombreAdjunto.trim())) {
            return true;
        }
        return esNombreAudio(texto);
    }

    // Verifica si el modelo todavia no tiene datos de adjunto utiles.
    private boolean esAdjuntoSinDatos(ChatMessageData mensaje) {
        if (mensaje == null) {
            return true;
        }
        boolean sinTipo = mensaje.getAttachmentType() == null || mensaje.getAttachmentType().trim().isEmpty();
        boolean sinUrl = mensaje.getAttachmentUrl() == null || mensaje.getAttachmentUrl().trim().isEmpty();
        boolean sinNombre = mensaje.getAttachmentFileName() == null || mensaje.getAttachmentFileName().trim().isEmpty();
        return sinTipo && sinUrl && sinNombre;
    }

    // Detecta extensiones de audio en nombres o urls.
    private boolean esRutaAudio(String valor) {
        if (valor == null) {
            return false;
        }
        String texto = valor.trim().toLowerCase();
        if (texto.startsWith("audio-") || texto.startsWith("voice-") || texto.equals("[audio]")) {
            return true;
        }
        return texto.contains(".webm")
                || texto.contains(".ogg")
                || texto.contains(".m4a")
                || texto.contains(".aac")
                || texto.contains(".mp3")
                || texto.contains(".wav");
    }
}
