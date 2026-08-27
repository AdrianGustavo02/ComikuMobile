package com.example.comiku.core.firebase;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import com.example.comiku.core.error.ErrorHandler;
import com.example.comiku.core.validation.MessageValidator;
import com.example.comiku.data.model.ChatMessageData;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Servicio para gestionar el envío, recepción y estados de mensajes
public class ChatMessageService {
    private static final String TAG = "ChatMessageService";
    private static final int PAGINA_SIZE = 20;
    private static final long TYPING_DEBOUNCE_MS = 2000; // 2 segundos
    private static final long STREAM_POLLING_MS = 2000; // 2 segundos
    private static final String CANAL_STREAM = "messaging";
    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;
    private final List<ListenerRegistration> listeners;
    private final Map<String, Long> ultimoEventoTyping = new HashMap<>(); // Debouncing por canal
    private final Context context;
    private final StreamChatSdkService streamSdkService;
    private final Handler streamPollingHandler;

    public ChatMessageService() {
        this(null);
    }

    public ChatMessageService(Context context) {
        this.firestore = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.listeners = new ArrayList<>();
        this.context = context != null ? context.getApplicationContext() : null;
        this.streamSdkService = this.context != null ? StreamChatSdkService.obtenerInstancia(this.context) : null;
        this.streamPollingHandler = new Handler(Looper.getMainLooper());
    }

    // Enviar un mensaje con validación y manejo de errores
    public void enviarMensaje(String channelId, String texto, SendMessageCallback callback) {
        enviarMensajeConAdjunto(channelId, null, texto, null, null, null, null, callback);
    }

    // Enviar un mensaje con adjunto opcional
    public void enviarMensajeConAdjunto(String channelId, String localMessageId, String texto, Uri attachmentUri,
                                        String attachmentType, String attachmentFileName, String attachmentMimeType,
                                        SendMessageCallback callback) {
        String usuarioId = auth.getCurrentUser().getUid();

        // Validar el mensaje
        boolean tieneAdjunto = attachmentUri != null;
        String textoLimpio = texto == null ? "" : texto.trim();
        if (!TextUtils.isEmpty(textoLimpio)) {
            MessageValidator.ValidationResult validacion = MessageValidator.validar(textoLimpio);
            if (!validacion.valido) {
                callback.onError(validacion.mensajeError);
                return;
            }
        } else if (!tieneAdjunto) {
            MessageValidator.ValidationResult validacion = MessageValidator.validar(texto);
            if (!validacion.valido) {
                callback.onError(validacion.mensajeError);
                return;
            }
        }

        String identificadorLocal = localMessageId;
        if (identificadorLocal == null || identificadorLocal.trim().isEmpty()) {
            identificadorLocal = "msg-" + System.currentTimeMillis();
        }

        if (streamSdkService != null) {
            String mensajeLocalId = identificadorLocal;
            streamSdkService.enviarMensaje(channelId, mensajeLocalId, texto, attachmentUri, attachmentType,
                    attachmentFileName, attachmentMimeType, new StreamChatSdkService.StreamSendCallback() {
                        @Override
                        public void onExito(ChatMessageData mensaje) {
                            guardarMensajeEnFirestore(channelId, mensaje);
                            actualizarCanalDespuesDeMensaje(channelId, construirPreviewMensaje(mensaje.getText(), mensaje.getAttachmentType()));
                            callback.onExito(mensaje);
                        }

                        @Override
                        public void onError(String error) {
                            if (!tieneAdjunto) {
                                guardarMensajeDirectoEnFirestore(channelId, mensajeLocalId, usuarioId, texto, attachmentType,
                                        null, attachmentFileName, callback);
                                return;
                            }
                            ErrorHandler.ErrorInfo errorInfo = ErrorHandler.obtenerInfoError(new Exception(error));
                            callback.onError(errorInfo.mensaje);
                        }
                    });
            return;
        }

        if (tieneAdjunto) {
            callback.onError("No se pudo conectar a StreamChat para enviar archivos adjuntos");
            return;
        }

        guardarMensajeDirectoEnFirestore(channelId, identificadorLocal, usuarioId, texto, attachmentType,
                null, attachmentFileName, callback);
    }

    // Cargar mensajes de un canal con paginación
    public void cargarMensajes(String channelId, int pagina, CargarMensajesCallback callback) {
        if (streamSdkService != null) {
            streamSdkService.cargarMensajesDesdeStream(channelId, new StreamChatSdkService.StreamMessagesCallback() {
                @Override
                public void onExito(List<ChatMessageData> mensajes) {
                    callback.onExito(mensajes);
                }

                @Override
                public void onError(String error) {
                    cargarMensajesDesdeFirestore(channelId, callback);
                }
            });
            return;
        }

        cargarMensajesDesdeFirestore(channelId, callback);
    }

    // Carga mensajes desde Firestore
    private void cargarMensajesDesdeFirestore(String channelId, CargarMensajesCallback callback) {
        firestore.collection("streamChannels")
                .document(channelId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(PAGINA_SIZE)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<ChatMessageData> mensajes = new ArrayList<>();
                    String usuarioActualId = auth.getCurrentUser().getUid();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        ChatMessageData mensaje = mapearMensajeDesdeDocumento(doc, channelId, usuarioActualId);
                        mensajes.add(mensaje);
                    }

                    callback.onExito(mensajes);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error cargando mensajes: " + e.getMessage());
                    callback.onError("Error cargando mensajes");
                });
    }

    // Escuchar mensajes nuevos en tiempo real
    public ListenerRegistration escucharMensajesEnTiempoReal(String channelId, 
                                                              MensajeEnTiempoRealCallback callback) {
        if (streamSdkService != null) {
            return escucharMensajesEnTiempoRealDesdeStream(channelId, callback);
        }

        return escucharMensajesEnTiempoRealDesdeFirestore(channelId, callback);
    }

    // Escucha cambios desde Firestore para compatibilidad.
    private ListenerRegistration escucharMensajesEnTiempoRealDesdeFirestore(String channelId,
                                                                           MensajeEnTiempoRealCallback callback) {
        String usuarioActualId = auth.getCurrentUser().getUid();
        
        ListenerRegistration listener = firestore.collection("streamChannels")
                .document(channelId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(PAGINA_SIZE)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error escuchando mensajes: " + error.getMessage());
                        callback.onError("Error cargando mensajes");
                        return;
                    }

                    if (snapshot == null || snapshot.isEmpty()) {
                        return;
                    }

                    // Procesar cambios en documentos
                    for (com.google.firebase.firestore.DocumentChange change : snapshot.getDocumentChanges()) {
                        DocumentSnapshot doc = change.getDocument();
                        ChatMessageData mensaje = mapearMensajeDesdeDocumento(doc, channelId, usuarioActualId);

                        // Notificar según el tipo de cambio
                        switch (change.getType()) {
                            case ADDED:
                                callback.onMensajeAñadido(mensaje);
                                break;
                            case MODIFIED:
                                callback.onMensajeModificado(mensaje);
                                break;
                            case REMOVED:
                                callback.onMensajeEliminado(mensaje.getId());
                                break;
                        }
                    }
                });

        listeners.add(listener);
        return listener;
    }

    // Escucha mensajes de StreamChat
    private ListenerRegistration escucharMensajesEnTiempoRealDesdeStream(String channelId,
                                                                         MensajeEnTiempoRealCallback callback) {
        final boolean[] activo = {true};
        final Map<String, ChatMessageData> cacheMensajes = new HashMap<>();
        final Runnable[] polling = new Runnable[1];

        polling[0] = new Runnable() {
            @Override
            public void run() {
                if (!activo[0]) {
                    return;
                }

                streamSdkService.cargarMensajesDesdeStream(channelId, new StreamChatSdkService.StreamMessagesCallback() {
                    @Override
                    public void onExito(List<ChatMessageData> mensajesActuales) {
                        Map<String, ChatMessageData> nuevosMensajes = new HashMap<>();
                        if (mensajesActuales != null) {
                            for (ChatMessageData mensaje : mensajesActuales) {
                                if (mensaje == null || mensaje.getId() == null) {
                                    continue;
                                }
                                nuevosMensajes.put(mensaje.getId(), mensaje);
                                ChatMessageData previo = cacheMensajes.get(mensaje.getId());
                                if (previo == null) {
                                    callback.onMensajeAñadido(mensaje);
                                } else if (mensajeCambio(previo, mensaje)) {
                                    callback.onMensajeModificado(mensaje);
                                }
                            }
                        }

                        for (String mensajeId : new ArrayList<>(cacheMensajes.keySet())) {
                            if (!nuevosMensajes.containsKey(mensajeId)) {
                                callback.onMensajeEliminado(mensajeId);
                            }
                        }

                        cacheMensajes.clear();
                        cacheMensajes.putAll(nuevosMensajes);

                        if (activo[0]) {
                            streamPollingHandler.postDelayed(polling[0], STREAM_POLLING_MS);
                        }
                    }

                    @Override
                    public void onError(String error) {
                        callback.onError(error);
                        if (activo[0]) {
                            streamPollingHandler.postDelayed(polling[0], STREAM_POLLING_MS);
                        }
                    }
                });
            }
        };

        ListenerRegistration listener = new ListenerRegistration() {
            @Override
            public void remove() {
                activo[0] = false;
                streamPollingHandler.removeCallbacks(polling[0]);
            }
        };

        listeners.add(listener);
        streamPollingHandler.post(polling[0]);
        return listener;
    }

    // Marcar un mensaje como leído
    public void marcarMensajeComoLeiido(String channelId, String mensajeId) {
        String usuarioId = auth.getCurrentUser().getUid();

        firestore.collection("streamChannels")
                .document(channelId)
                .collection("messages")
                .document(mensajeId)
                .update(
                        "status", "read",
                        "readBy", FieldValue.increment(1)
                )
                .addOnFailureListener(e -> Log.e(TAG, "Error marcando mensaje como leído: " + e.getMessage()));
    }

    // Marcar todos los mensajes de un canal como leídos
    public void marcarCanalComoLeiido(String channelId) {
        firestore.collection("streamChannels")
                .document(channelId)
                .collection("messages")
                .whereNotEqualTo("userId", auth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        doc.getReference().update("status", "read");
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error marcando canal como leído: " + e.getMessage()));
    }

     // Actualizar información del canal después de enviar un mensaje
    private void actualizarCanalDespuesDeMensaje(String channelId, String ultimoMensaje) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastMessage", ultimoMensaje.substring(0, Math.min(50, ultimoMensaje.length())));
        updates.put("lastMessageAt", new Date());

        firestore.collection("streamChannels")
                .document(channelId)
                .update(updates)
                .addOnFailureListener(e -> {
                    ErrorHandler.ErrorInfo errorInfo = ErrorHandler.obtenerInfoError(e);
                    Log.e(TAG, "Error actualizando canal: " + errorInfo.mensaje);
                });
    }

    // Guarda el mensaje enviado por StreamChat en Firestore
    private void guardarMensajeEnFirestore(String channelId, ChatMessageData mensaje) {
        Map<String, Object> mensajeData = mensaje.toMap();
        firestore.collection("streamChannels")
                .document(channelId)
                .collection("messages")
                .document(mensaje.getId())
                .set(mensajeData)
                .addOnFailureListener(e -> Log.e(TAG, "Error guardando mensaje: " + e.getMessage()));
    }

    // Guarda un mensaje sin Stream cuando solo hay texto disponible.
    private void guardarMensajeDirectoEnFirestore(String channelId, String messageId, String usuarioId, String texto,
                                                  String attachmentType, String attachmentUrl, String attachmentFileName,
                                                  SendMessageCallback callback) {
        Map<String, Object> mensajeData = new HashMap<>();
        mensajeData.put("id", messageId);
        mensajeData.put("userId", usuarioId);
        mensajeData.put("text", texto == null ? "" : texto.trim());
        mensajeData.put("createdAt", new Date());
        mensajeData.put("status", "sent");
        mensajeData.put("readBy", 0);
        mensajeData.put("intentoReenvio", 0);
        mensajeData.put("attachmentType", attachmentType);
        mensajeData.put("attachmentUrl", attachmentUrl);
        mensajeData.put("attachmentFileName", attachmentFileName);
        mensajeData.put("streamMessageId", null);

        firestore.collection("streamChannels")
                .document(channelId)
                .collection("messages")
                .document(messageId)
                .set(mensajeData)
                .addOnSuccessListener(docRef -> {
                    Log.d(TAG, "Mensaje guardado: " + messageId);
                    actualizarCanalDespuesDeMensaje(channelId, construirPreviewMensaje(texto, attachmentType));

                    ChatMessageData mensaje = new ChatMessageData();
                    mensaje.setId(messageId);
                    mensaje.setUserId(usuarioId);
                    mensaje.setText(texto == null ? "" : texto.trim());
                    mensaje.setCreatedAt(new Date());
                    mensaje.setStatus("sent");
                    mensaje.setAttachmentType(attachmentType);
                    mensaje.setAttachmentUrl(attachmentUrl);
                    mensaje.setAttachmentFileName(attachmentFileName);
                    callback.onExito(mensaje);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error guardando mensaje: " + e.getMessage());
                    ErrorHandler.ErrorInfo errorInfo = ErrorHandler.obtenerInfoError(e);
                    callback.onError(errorInfo.mensaje);
                });
    }

    // Construir resumen de ultimo mensaje segun tipo
    private String construirPreviewMensaje(String texto, String attachmentType) {
        String textoLimpio = texto == null ? "" : texto.trim();
        if (attachmentType == null || attachmentType.trim().isEmpty()) {
            return textoLimpio;
        }
        if ("image".equals(attachmentType)) {
            return textoLimpio.isEmpty() ? "[Imagen]" : "[Imagen] " + textoLimpio;
        }
        if ("audio".equals(attachmentType)) {
            return textoLimpio.isEmpty() ? "[Audio]" : "[Audio] " + textoLimpio;
        }
        return textoLimpio.isEmpty() ? "[Adjunto]" : "[Adjunto] " + textoLimpio;
    }

    // Convierte un documento Firestore al modelo local y normaliza archivos adjuntos.
    private ChatMessageData mapearMensajeDesdeDocumento(DocumentSnapshot doc, String channelId, String usuarioActualId) {
        ChatMessageData mensaje = new ChatMessageData();
        mensaje.setId(doc.getId());
        mensaje.setChannelId(channelId);
        mensaje.setUserId(doc.getString("userId"));
        mensaje.setText(doc.getString("text"));
        mensaje.setAttachmentType(doc.getString("attachmentType"));
        mensaje.setAttachmentUrl(doc.getString("attachmentUrl"));
        mensaje.setAttachmentFileName(doc.getString("attachmentFileName"));
        mensaje.setStreamMessageId(doc.getString("streamMessageId"));
        mensaje.setStatus(doc.getString("status"));
        mensaje.setCreatedAt(doc.getTimestamp("createdAt") != null
                ? doc.getTimestamp("createdAt").toDate()
                : new Date());
        mensaje.setReadBy(Math.toIntExact(doc.getLong("readBy") != null ? doc.getLong("readBy") : 0));
        mensaje.setOwn(TextUtils.equals(mensaje.getUserId(), usuarioActualId));

        normalizarAdjuntoDesdeFirestore(doc, mensaje);
        return mensaje;
    }


    private void normalizarAdjuntoDesdeFirestore(DocumentSnapshot doc, ChatMessageData mensaje) {
        if (mensaje == null || doc == null) {
            return;
        }

        if (!esVacio(mensaje.getAttachmentType()) || !esVacio(mensaje.getAttachmentUrl()) || !esVacio(mensaje.getAttachmentFileName())) {
            return;
        }

        Object adjuntosRaw = doc.get("attachments");
        if (!(adjuntosRaw instanceof List)) {
            if (esNombreAudio(mensaje.getText())) {
                mensaje.setAttachmentType("audio");
                mensaje.setAttachmentFileName(mensaje.getText().trim());
                mensaje.setText("");
            }
            return;
        }

        List<?> adjuntos = (List<?>) adjuntosRaw;
        if (adjuntos.isEmpty() || !(adjuntos.get(0) instanceof Map)) {
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> adjunto = (Map<String, Object>) adjuntos.get(0);
        String tipo = valorComoTexto(adjunto.get("type")).toLowerCase();
        String mime = valorComoTexto(adjunto.get("mime_type")).toLowerCase();
        String url = valorComoTexto(adjunto.get("asset_url"));
        if (url.isEmpty()) {
            url = valorComoTexto(adjunto.get("image_url"));
        }
        if (url.isEmpty()) {
            url = valorComoTexto(adjunto.get("url"));
        }
        String nombre = valorComoTexto(adjunto.get("name"));
        if (nombre.isEmpty()) {
            nombre = valorComoTexto(adjunto.get("title"));
        }

        if (tipo.isEmpty()) {
            if (mime.startsWith("audio/") || esNombreAudio(nombre) || esNombreAudio(url) || esNombreAudio(mensaje.getText())) {
                tipo = "audio";
            } else if (mime.startsWith("image/")) {
                tipo = "image";
            } else {
                tipo = "file";
            }
        }

        mensaje.setAttachmentType(tipo);
        mensaje.setAttachmentUrl(url);
        mensaje.setAttachmentFileName(nombre);

        if ("audio".equals(tipo) && esTextoSoloNombreAudio(mensaje.getText(), nombre)) {
            mensaje.setText("");
        }
    }

    // Devuelve true cuando un texto esta vacio.
    private boolean esVacio(String valor) {
        return valor == null || valor.trim().isEmpty();
    }

    // Convierte cualquier valor en texto seguro.
    private String valorComoTexto(Object valor) {
        return valor == null ? "" : String.valueOf(valor).trim();
    }

    // Detecta si un valor parece nombre o url de audio.
    private boolean esNombreAudio(String valor) {
        if (valor == null) {
            return false;
        }
        String texto = valor.trim().toLowerCase();
        return texto.startsWith("audio-")
                || texto.startsWith("voice-")
                || texto.equals("[audio]")
                || texto.contains(".webm")
                || texto.contains(".ogg")
                || texto.contains(".m4a")
                || texto.contains(".aac")
                || texto.contains(".mp3")
                || texto.contains(".wav");
    }

    // Detecta cuando el texto solo repite el nombre del audio.
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

    // Limpiar todos los listeners
    public void limpiarListeners() {
        for (ListenerRegistration listener : listeners) {
            listener.remove();
        }
        listeners.clear();
        ultimoEventoTyping.clear();
        streamPollingHandler.removeCallbacksAndMessages(null);
    }

    // Notificar que el usuario está escribiendo
    public void notificarEscribiendo(String channelId) {
        String usuarioId = auth.getCurrentUser().getUid();
        
        Long ultimoEvento = ultimoEventoTyping.getOrDefault(channelId, 0L);
        long ahora = System.currentTimeMillis();
        
        // Solo enviar evento si ha pasado el tiempo de debounce
        if (ahora - ultimoEvento >= TYPING_DEBOUNCE_MS) {
            ultimoEventoTyping.put(channelId, ahora);
            
            Map<String, Object> typingData = new HashMap<>();
            typingData.put("userId", usuarioId);
            typingData.put("timestamp", new Date());
            
            firestore.collection("streamChannels")
                    .document(channelId)
                    .collection("typingIndicators")
                    .document(usuarioId)
                    .set(typingData)
                    .addOnFailureListener(e -> Log.e(TAG, "Error enviando typing indicator: " + e.getMessage()));
        }
    }

    // Limpiar evento de escritura cuando termina
    public void limpiarEscribiendo(String channelId) {
        String usuarioId = auth.getCurrentUser().getUid();
        ultimoEventoTyping.remove(channelId);
        
        firestore.collection("streamChannels")
                .document(channelId)
                .collection("typingIndicators")
                .document(usuarioId)
                .delete()
                .addOnFailureListener(e -> Log.e(TAG, "Error limpiando typing indicator: " + e.getMessage()));
    }

    // Escuchar eventos de escritura en tiempo real
    public ListenerRegistration escucharEscribiendo(String channelId, String otroUsuarioId, 
                                                     TypingIndicatorCallback callback) {
        ListenerRegistration listener = firestore.collection("streamChannels")
                .document(channelId)
                .collection("typingIndicators")
                .document(otroUsuarioId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error escuchando typing: " + error.getMessage());
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        Date timestamp = snapshot.getTimestamp("timestamp") != null ?
                                snapshot.getTimestamp("timestamp").toDate() : new Date();
                        long ahora = System.currentTimeMillis();
                        long tiempoTranscurrido = ahora - timestamp.getTime();
                        
                        // Si el evento tiene menos de 5 segundos, mostrar "escribiendo"
                        if (tiempoTranscurrido < 5000) {
                            callback.onEscribiendo(true);
                        } else {
                            callback.onEscribiendo(false);
                        }
                    } else {
                        callback.onEscribiendo(false);
                    }
                });

        listeners.add(listener);
        return listener;
    }

    // Interfaces de callback
    public interface SendMessageCallback {
        void onExito(ChatMessageData mensaje);
        void onError(String error);
    }

    public interface CargarMensajesCallback {
        void onExito(List<ChatMessageData> mensajes);
        void onError(String error);
    }

    public interface MensajeEnTiempoRealCallback {
        void onMensajeAñadido(ChatMessageData mensaje);
        void onMensajeModificado(ChatMessageData mensaje);
        void onMensajeEliminado(String mensajeId);
        void onError(String error);
    }

    public interface TypingIndicatorCallback {
        void onEscribiendo(boolean estaEscribiendo);
    }

    // Detecta cambios relevantes para refrescar un mensaje en pantalla.
    private boolean mensajeCambio(ChatMessageData previo, ChatMessageData actual) {
        if (previo == null || actual == null) {
            return true;
        }
        String textoPrevio = previo.getText() == null ? "" : previo.getText();
        String textoActual = actual.getText() == null ? "" : actual.getText();
        if (!textoPrevio.equals(textoActual)) {
            return true;
        }

        String adjuntoPrevio = previo.getAttachmentUrl() == null ? "" : previo.getAttachmentUrl();
        String adjuntoActual = actual.getAttachmentUrl() == null ? "" : actual.getAttachmentUrl();
        if (!adjuntoPrevio.equals(adjuntoActual)) {
            return true;
        }

        String estadoPrevio = previo.getStatus() == null ? "" : previo.getStatus();
        String estadoActual = actual.getStatus() == null ? "" : actual.getStatus();
        if (!estadoPrevio.equals(estadoActual)) {
            return true;
        }

        long fechaPrevio = previo.getCreatedAt() != null ? previo.getCreatedAt().getTime() : 0L;
        long fechaActual = actual.getCreatedAt() != null ? actual.getCreatedAt().getTime() : 0L;
        return fechaPrevio != fechaActual;
    }
}
