package com.example.comiku.screens;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.comiku.R;
import com.example.comiku.core.ui.DeleteConfirmDialogComponent;
import com.example.comiku.core.ui.ToastUtils;
import com.example.comiku.core.firebase.ChatChannelService;
import com.example.comiku.core.firebase.PrivacyService;
import com.example.comiku.core.firebase.StreamChatAuthService;
import com.example.comiku.core.firebase.StreamChatSdkService;
import com.example.comiku.data.model.ChatChannelData;
import com.example.comiku.data.repository.FriendshipRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import io.getstream.chat.android.ui.ChatUI;
import io.getstream.chat.android.ui.common.helper.DateFormatter;
import io.getstream.chat.android.ui.common.state.messages.MessageMode;
import io.getstream.chat.android.models.Attachment;
import io.getstream.chat.android.ui.feature.messages.composer.MessageComposerView;
import io.getstream.chat.android.ui.feature.messages.list.MessageListView;
import io.getstream.chat.android.ui.feature.messages.list.options.message.MessageOptionItem;
import io.getstream.chat.android.ui.feature.messages.list.options.message.MessageOptionItemsFactory;
import io.getstream.chat.android.ui.common.state.messages.BlockUser;
import io.getstream.chat.android.ui.common.state.messages.Edit;
import io.getstream.chat.android.ui.common.state.messages.Flag;
import io.getstream.chat.android.ui.common.state.messages.MarkAsUnread;
import io.getstream.chat.android.ui.common.state.messages.Pin;
import io.getstream.chat.android.ui.common.state.messages.Reply;
import io.getstream.chat.android.ui.common.state.messages.ThreadReply;
import io.getstream.chat.android.ui.common.state.messages.UnblockUser;
import io.getstream.chat.android.ui.viewmodel.messages.MessageComposerViewModel;
import io.getstream.chat.android.ui.viewmodel.messages.MessageComposerViewModelBinding;
import io.getstream.chat.android.ui.viewmodel.messages.MessageListViewModel;
import io.getstream.chat.android.ui.viewmodel.messages.MessageListViewModelBinding;
import io.getstream.chat.android.ui.viewmodel.messages.MessageListViewModelFactory;

// Activity que muestra la conversacion usando los componentes nativos de Stream Chat
public class ChatViewActivity extends BasePlainScreenActivity {

    // Vistas del encabezado
    private RoundedImageView fotoOtroUsuario;
    private TextView nombreOtroUsuario;
    private TextView estadoOtroUsuario;
    private View encabezadoChat;

    // Componentes de Stream Chat
    private MessageListView messageListView;
    private MessageComposerView messageComposerView;

    // Vistas de estado
    private ProgressBar indicadorCargaMensajes;
    private LinearLayout estadoError;
    private ImageButton botonOpciones;

    // Servicios
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;
    private ChatChannelService channelService;
    private PrivacyService privacyService;
    private StreamChatAuthService authStreamService;
    private StreamChatSdkService streamSdkService;

    // Estado del canal
    private String channelId;
    private String otherUserId;
    private String channelType;
    private String usuarioActualId;
    private boolean isGroupChat = false;
    private boolean creatingNewChannel = false;
    private ChatChannelData canalActual;
    private MessageListViewModel listViewModel;
    private MessageComposerViewModel composerViewModel;
    private AlertDialog audioDialog;
    private ExoPlayer reproductorAudioDialog;
    private static final TimeZone ZONA_HORARIA_STREAM = TimeZone.getTimeZone("America/Argentina/Buenos_Aires");
    private boolean grabandoAudio = false;
    private boolean audioBloqueado = false;
    private float yInicialGrabacion = 0f;
    private float xInicialGrabacion = 0f;
    private static final float UMBRAL_BLOQUEO_AUDIO_DP = 72f;
    private static final float UMBRAL_CANCELAR_AUDIO_DP = 96f;
    private static final String OPCION_INFO_MIEMBROS = "Ver informacion y miembros";
    private static final String OPCION_BORRAR_GRUPO = "Borrar grupo";
    private static final String OPCION_ABANDONAR_GRUPO = "Abandonar grupo";
    private ListenerRegistration escuchadorBloqueoPropio;
    private ListenerRegistration escuchadorBloqueoAjeno;
    private boolean bloqueadoPorMi = false;
    private boolean bloqueadoPorOtro = false;
    private Boolean ultimoEstadoEnvioBloqueado = null;

    // Oculta el remate superior para alinear el header con la barra de estado.
    @Override
    protected boolean shouldShowTopCap() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupPlainScreenShell(R.layout.activity_chat_view);

        inicializarFirebase();
        inicializarServicios();
        configurarDateFormatterStream();
        procesarExtras();
        inicializarVistas();
        configurarHeaderBotones();

        if (creatingNewChannel && otherUserId != null) {
            crearNuevoCanal();
        } else if (channelId != null) {
            cargarInformacionCanalActual();
            iniciarChat();
        }
    }

    // Vincula las vistas del layout con las variables de la clase
    private void inicializarVistas() {
        fotoOtroUsuario = findViewById(R.id.fotoOtroUsuario);
        fotoOtroUsuario.setCircular(true);
        nombreOtroUsuario = findViewById(R.id.nombreOtroUsuario);
        nombreOtroUsuario.setTextColor(Color.WHITE);
        estadoOtroUsuario = findViewById(R.id.estadoOtroUsuario);
        encabezadoChat = findViewById(R.id.encabezadoChat);
        botonOpciones = findViewById(R.id.botonOpciones);
        messageListView = findViewById(R.id.messageListView);
        messageComposerView = findViewById(R.id.messageComposerView);
        indicadorCargaMensajes = findViewById(R.id.indicadorCargaMensajes);
        estadoError = findViewById(R.id.estadoError);
        aplicarColorBotonEnviar();
        aplicarEstiloInputMensaje();

        if (messageListView != null) {
            messageListView.setMessageOptionItemsFactory(new MessageOptionItemsFactory() {
                @Override
                public List<MessageOptionItem> createMessageOptionItems(
                        io.getstream.chat.android.models.Message selectedMessage,
                        io.getstream.chat.android.models.User currentUser,
                        boolean isInThread,
                        java.util.Set<String> ownCapabilities,
                        io.getstream.chat.android.ui.feature.messages.list.MessageListViewStyle style) {
                    List<MessageOptionItem> opciones = MessageOptionItemsFactory.Companion.defaultFactory(ChatViewActivity.this)
                            .createMessageOptionItems(selectedMessage, currentUser, isInThread, ownCapabilities, style);
                    List<MessageOptionItem> opcionesFiltradas = new ArrayList<>();
                    for (MessageOptionItem opcion : opciones) {
                        if (opcion == null) {
                            continue;
                        }

                        Object accion = opcion.getMessageAction();
                        if (accion instanceof MarkAsUnread
                                || accion instanceof Reply
                                || accion instanceof Flag
                                || accion instanceof Pin
                                || accion instanceof BlockUser
                                || accion instanceof UnblockUser) {
                            continue;
                        }
                        opcionesFiltradas.add(opcion);
                    }
                    return opcionesFiltradas;
                }
            });
        }

        Button botonReintentar = findViewById(R.id.botonReintentarMensajes);
        if (botonReintentar != null) {
            botonReintentar.setOnClickListener(v -> iniciarChat());
        }
    }

    // Aplica el color naranja del boton primario al boton enviar de Stream.
    private void aplicarColorBotonEnviar() {
        if (messageComposerView == null) {
            return;
        }
        View botonEnviar = messageComposerView.findViewById(io.getstream.chat.android.ui.R.id.sendMessageButton);
        if (botonEnviar == null) {
            return;
        }
        int colorNaranja = ContextCompat.getColor(this, R.color.primary_button_orange_flat);
        botonEnviar.setBackgroundTintList(ColorStateList.valueOf(colorNaranja));
        if (botonEnviar instanceof ImageView) {
            ((ImageView) botonEnviar).setImageTintList(ColorStateList.valueOf(Color.WHITE));
        }
    }

    // Pone en blanco el icono del microfono del composer.
    private void aplicarColorBotonMicrofono() {
        if (messageComposerView == null) {
            return;
        }
        View botonMicrofono = messageComposerView.findViewById(io.getstream.chat.android.ui.R.id.recordAudioButton);
        if (!(botonMicrofono instanceof ImageView)) {
            return;
        }
        ((ImageView) botonMicrofono).setImageTintList(ColorStateList.valueOf(Color.WHITE));
    }

    // Pone en blanco el icono del clip del composer.
    private void aplicarColorBotonAdjuntos() {
        if (messageComposerView == null) {
            return;
        }
        View botonAdjuntos = messageComposerView.findViewById(io.getstream.chat.android.ui.R.id.attachmentsButton);
        if (!(botonAdjuntos instanceof ImageView)) {
            return;
        }
        ((ImageView) botonAdjuntos).setImageTintList(ColorStateList.valueOf(Color.WHITE));
    }

    // Aplica estilo visual al input del chat.
    private void aplicarEstiloInputMensaje() {
        if (messageComposerView == null) {
            return;
        }
        int colorNavbar = Color.parseColor("#12091D");
        int colorHint = Color.parseColor("#cdbfe3");
        messageComposerView.setBackgroundColor(colorNavbar);
        EditText campoMensaje = findEditTextInView(messageComposerView);
        if (campoMensaje == null) {
            return;
        }
        campoMensaje.setBackgroundTintList(ColorStateList.valueOf(colorNavbar));
        campoMensaje.setTextColor(Color.WHITE);
        campoMensaje.setHintTextColor(colorHint);
    }

    // Busca el EditText del composer dentro del arbol de vistas.
    private EditText findEditTextInView(View vistaRaiz) {
        if (vistaRaiz == null) {
            return null;
        }
        if (vistaRaiz instanceof EditText) {
            return (EditText) vistaRaiz;
        }
        if (!(vistaRaiz instanceof ViewGroup)) {
            return null;
        }
        ViewGroup grupo = (ViewGroup) vistaRaiz;
        for (int indice = 0; indice < grupo.getChildCount(); indice++) {
            EditText campoEncontrado = findEditTextInView(grupo.getChildAt(indice));
            if (campoEncontrado != null) {
                return campoEncontrado;
            }
        }
        return null;
    }

    // Inicializa las instancias de Firebase
    private void inicializarFirebase() {
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    // Inicializa los servicios que se usan en el chat
    private void inicializarServicios() {
        channelService = new ChatChannelService(this);
        privacyService = new PrivacyService();
        authStreamService = StreamChatAuthService.obtenerInstancia(this);
        streamSdkService = StreamChatSdkService.obtenerInstancia(this);
    }

    // Lee los datos enviados al abrir esta pantalla
    private void procesarExtras() {
        Intent intent = getIntent();
        channelId = intent.getStringExtra("channelId");
        otherUserId = intent.getStringExtra("otherUserId");
        channelType = intent.getStringExtra("channelType");
        creatingNewChannel = intent.getBooleanExtra("createNewChannel", false);
        isGroupChat = intent.getBooleanExtra("isGroupChat", false) || "group".equals(channelType);

        if (auth.getCurrentUser() == null) {
            finish();
            return;
        }
        usuarioActualId = auth.getCurrentUser().getUid();
        iniciarEscuchaBloqueoSiAplica();
    }

    // Configura los clics del encabezado que no dependen del canal cargado
    private void configurarHeaderBotones() {
        if (botonOpciones != null) botonOpciones.setOnClickListener(v -> abrirOpcionesCanal());

        if (encabezadoChat != null) {
            encabezadoChat.setOnClickListener(v -> {
                if (isGroupChat) {
                    abrirInformacionMiembrosGrupo();
                }
            });
        }
        nombreOtroUsuario.setOnClickListener(v -> {
            if (isGroupChat) {
                abrirInformacionMiembrosGrupo();
                return;
            }
            abrirPerfilOtroUsuario();
        });
        fotoOtroUsuario.setOnClickListener(v -> {
            if (isGroupChat) {
                abrirInformacionMiembrosGrupo();
                return;
            }
            abrirPerfilOtroUsuario();
        });
        estadoOtroUsuario.setOnClickListener(v -> {
            if (isGroupChat) {
                abrirInformacionMiembrosGrupo();
            }
        });
    }

    // Valida la privacidad del canal y luego conecta Stream Chat
    private void iniciarChat() {
        estadoError.setVisibility(View.GONE);
        indicadorCargaMensajes.setVisibility(View.VISIBLE);

        if (!isGroupChat && otherUserId != null && !creatingNewChannel) {
            privacyService.validarPuedoChateaR(otherUserId, new PrivacyService.PrivacyCheckCallback() {
                @Override
                public void onExito() {
                    iniciarEscuchaBloqueoSiAplica();
                    cargarInformacionOtroUsuario();
                    conectarStreamUI();
                }

                @Override
                public void onError(String razon) {
                    mostrarError(razon);
                }
            });
        } else {
            conectarStreamUI();
        }
        actualizarVisibilidadOpcionesGrupo();
    }

    // Asegura que el cliente de Stream este autenticado y luego muestra la UI
    private void conectarStreamUI() {
        String apiKey = authStreamService != null ? authStreamService.obtenerStreamApiKey() : null;
        String token = authStreamService != null ? authStreamService.obtenerStreamToken() : null;

        if (!TextUtils.isEmpty(apiKey) && !TextUtils.isEmpty(token)) {
            streamSdkService.conectarConCredenciales(apiKey, token, new StreamChatSdkService.StreamClientCallback() {
                @Override
                public void onExito(io.getstream.chat.android.client.ChatClient cliente) {
                    runOnUiThread(() -> inicializarStreamUI(obtenerCidStream()));
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> mostrarError("No se pudo conectar al chat: " + error));
                }
            });
            return;
        }

        streamSdkService.asegurarCliente(new StreamChatSdkService.StreamClientCallback() {
            @Override
            public void onExito(io.getstream.chat.android.client.ChatClient cliente) {
                runOnUiThread(() -> inicializarStreamUI(obtenerCidStream()));
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> mostrarError("No se pudo conectar al chat: " + error));
            }
        });
    }

    // Inicializa los ViewModels de Stream y los vincula a las vistas
    private void inicializarStreamUI(String cid) {
        if (isFinishing() || isDestroyed()) return;
        if (TextUtils.isEmpty(cid)) {
            mostrarError("No se pudo abrir el canal");
            return;
        }

        try {
            io.getstream.chat.android.client.ChatClient chatClient = streamSdkService.obtenerClienteActual();
            if (chatClient == null) {
                mostrarError("No se pudo abrir el chat");
                return;
            }

            MessageListViewModelFactory factory = new MessageListViewModelFactory(this, cid, null, null, chatClient);
            listViewModel = new ViewModelProvider(this, factory)
                    .get(MessageListViewModel.class);
            composerViewModel = new ViewModelProvider(this, factory)
                    .get(MessageComposerViewModel.class);

            // Vincula cada ViewModel a su vista nativa de Stream
            MessageListViewModelBinding.bind(listViewModel, messageListView, this);
            MessageComposerViewModelBinding.bind(composerViewModel, messageComposerView, this);
            aplicarEstiloInputMensaje();
            aplicarColorBotonMicrofono();
            aplicarColorBotonAdjuntos();
            messageListView.setMessageEditHandler(mensaje -> composerViewModel.performMessageAction(
                    new Edit(mensaje)
            ));
            configurarGrabacionManual();
            messageListView.setOnAttachmentClickListener((mensaje, attachment) -> {
                if (esImagenAdjunta(attachment)) {
                    abrirVistaPreviaImagen(attachment);
                    return true;
                }
                return false;
            });
            listViewModel.getMode().observe(this, modo -> {
                if (modo instanceof MessageMode.MessageThread) {
                    composerViewModel.setMessageMode(modo);
                } else {
                    composerViewModel.leaveThread();
                }
            });

            getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (composerViewModel != null
                            && composerViewModel.getMessageMode().getValue() instanceof MessageMode.MessageThread) {
                        listViewModel.onEvent(MessageListViewModel.Event.BackButtonPressed.INSTANCE);
                        composerViewModel.leaveThread();
                        return;
                    }
                    finish();
                }
            });

            messageListView.setVisibility(View.VISIBLE);
            messageComposerView.setVisibility(View.VISIBLE);
            indicadorCargaMensajes.setVisibility(View.GONE);
            estadoError.setVisibility(View.GONE);
            actualizarVisibilidadOpcionesGrupo();

            channelService.marcarCanalComoLeiido(channelId);
        } catch (Exception error) {
            String detalle = error.getMessage() != null ? error.getMessage() : "desconocido";
            mostrarError("Error al iniciar el chat: " + detalle);
        }
    }

    // Configura el boton de microfono para grabar con un gesto simple y estable.
    private void configurarGrabacionManual() {
        if (messageComposerView == null) {
            return;
        }
        messageComposerView.setAudioRecordButtonTouchListener(event -> {
            if (composerViewModel == null) {
                return false;
            }

            int accion = event.getActionMasked();
            if (accion == MotionEvent.ACTION_DOWN) {
                grabandoAudio = true;
                audioBloqueado = false;
                yInicialGrabacion = event.getRawY();
                xInicialGrabacion = event.getRawX();
                composerViewModel.startRecording();
                return true;
            }

            if (!grabandoAudio) {
                return false;
            }

            float deltaY = yInicialGrabacion - event.getRawY();
            float deltaX = xInicialGrabacion - event.getRawX();
            float umbralBloqueo = UMBRAL_BLOQUEO_AUDIO_DP * getResources().getDisplayMetrics().density;
            float umbralCancelar = UMBRAL_CANCELAR_AUDIO_DP * getResources().getDisplayMetrics().density;

            if (accion == MotionEvent.ACTION_MOVE && !audioBloqueado && deltaY > umbralBloqueo) {
                audioBloqueado = true;
                composerViewModel.lockRecording();
                return true;
            }

            if (accion == MotionEvent.ACTION_MOVE && !audioBloqueado && deltaX > umbralCancelar) {
                grabandoAudio = false;
                composerViewModel.cancelRecording();
                return true;
            }

            if (accion == MotionEvent.ACTION_UP) {
                grabandoAudio = false;
                if (!audioBloqueado) {
                    composerViewModel.completeRecording();
                }
                return true;
            }

            if (accion == MotionEvent.ACTION_CANCEL) {
                grabandoAudio = false;
                audioBloqueado = false;
                composerViewModel.cancelRecording();
                return true;
            }

            return true;
        });
    }

    // Abre la imagen del mensaje en una pantalla completa.
    private void abrirVistaPreviaImagen(Attachment attachment) {
        String urlImagen = obtenerUrlImagenAdjunta(attachment);
        if (TextUtils.isEmpty(urlImagen)) {
            ToastUtils.showTextToast(this, "No se pudo abrir la imagen", Toast.LENGTH_SHORT);
            return;
        }

        Intent intent = new Intent(this, ImagePreviewActivity.class);
        intent.putExtra("imageUrl", urlImagen);
        startActivity(intent);
    }

    // Devuelve la URL correcta de una imagen usando el valor mas util disponible.
    private String obtenerUrlImagenAdjunta(Attachment attachment) {
        if (attachment == null) {
            return null;
        }
        if (!TextUtils.isEmpty(attachment.getImageUrl())) {
            return attachment.getImageUrl();
        }
        if (!TextUtils.isEmpty(attachment.getThumbUrl())) {
            return attachment.getThumbUrl();
        }
        return null;
    }

    // Indica si el adjunto es una imagen que se puede abrir en pantalla completa.
    private boolean esImagenAdjunta(Attachment attachment) {
        return attachment != null && "image".equalsIgnoreCase(attachment.getType());
    }

    // Indica si el adjunto debe abrirse con el reproductor de audio.
    private boolean esAudioAdjunto(Attachment attachment) {
        if (attachment == null) {
            return false;
        }

        String tipo = attachment.getType() == null ? "" : attachment.getType().trim().toLowerCase();
        if ("audio".equals(tipo)) {
            return true;
        }

        String mime = attachment.getMimeType() == null ? "" : attachment.getMimeType().trim().toLowerCase();
        if (mime.startsWith("audio/")) {
            return true;
        }

        String nombre = attachment.getName() == null ? "" : attachment.getName().trim().toLowerCase();
        String titulo = attachment.getTitle() == null ? "" : attachment.getTitle().trim().toLowerCase();
        String assetUrl = attachment.getAssetUrl() == null ? "" : attachment.getAssetUrl().trim().toLowerCase();
        String imageUrl = attachment.getImageUrl() == null ? "" : attachment.getImageUrl().trim().toLowerCase();

        return esRutaAudio(nombre)
                || esRutaAudio(titulo)
                || esRutaAudio(assetUrl)
                || esRutaAudio(imageUrl);
    }

    // Detecta si una ruta o nombre parece corresponder a un audio.
    private boolean esRutaAudio(String valor) {
        if (valor == null) {
            return false;
        }
        String texto = valor.trim().toLowerCase();
        if (texto.isEmpty()) {
            return false;
        }
        return texto.contains(".webm")
                || texto.contains(".ogg")
                || texto.contains(".m4a")
                || texto.contains(".aac")
                || texto.contains(".mp3")
                || texto.contains(".wav");
    }

    // Abre un dialogo con el reproductor de audio dentro de la pantalla del chat.
    private void abrirReproductorAudio(Attachment attachment) {
        String urlAudio = obtenerUrlAudioAdjunto(attachment);
        if (TextUtils.isEmpty(urlAudio)) {
            ToastUtils.showTextToast(this, "No se pudo abrir el audio", Toast.LENGTH_SHORT);
            return;
        }

        cerrarReproductorAudio();

        View vistaDialogo = LayoutInflater.from(this).inflate(R.layout.dialog_audio_attachment, null, false);
        TextView textoNombreAudio = vistaDialogo.findViewById(R.id.textoNombreAudio);
        TextView textoEstadoAudio = vistaDialogo.findViewById(R.id.textoEstadoAudio);
        Button botonReproducirAudio = vistaDialogo.findViewById(R.id.botonReproducirAudioDialogo);
        Button botonCerrarAudio = vistaDialogo.findViewById(R.id.botonCerrarAudioDialogo);

        String nombreAudio = obtenerNombreAudioAdjunto(attachment);
        textoNombreAudio.setText(nombreAudio);
        textoEstadoAudio.setText(R.string.audio_listo);

        reproductorAudioDialog = new ExoPlayer.Builder(this).build();
        MediaItem mediaItem = MediaItem.fromUri(urlAudio);
        reproductorAudioDialog.setMediaItem(mediaItem);
        reproductorAudioDialog.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    textoEstadoAudio.setText(R.string.audio_reproduciendo);
                    botonReproducirAudio.setText(R.string.audio_pausar);
                } else if (playbackState == Player.STATE_ENDED) {
                    textoEstadoAudio.setText(R.string.audio_finalizado);
                    botonReproducirAudio.setText(R.string.reproducir);
                }
            }
        });

        botonReproducirAudio.setOnClickListener(v -> {
            if (reproductorAudioDialog == null) {
                return;
            }
            if (reproductorAudioDialog.isPlaying()) {
                reproductorAudioDialog.pause();
                botonReproducirAudio.setText(R.string.reproducir);
                textoEstadoAudio.setText(R.string.audio_pausado);
            } else {
                reproductorAudioDialog.prepare();
                reproductorAudioDialog.play();
            }
        });

        botonCerrarAudio.setOnClickListener(v -> cerrarReproductorAudio());

        audioDialog = new AlertDialog.Builder(this)
                .setView(vistaDialogo)
                .setCancelable(true)
                .create();
        audioDialog.setOnDismissListener(dialog -> cerrarReproductorAudio());
        audioDialog.show();
    }

    // Devuelve la URL util para reproducir un audio recibido.
    private String obtenerUrlAudioAdjunto(Attachment attachment) {
        if (attachment == null) {
            return null;
        }
        if (!TextUtils.isEmpty(attachment.getAssetUrl())) {
            return attachment.getAssetUrl();
        }
        if (!TextUtils.isEmpty(attachment.getImageUrl())) {
            return attachment.getImageUrl();
        }

        Map<String, Object> extraData = attachment.getExtraData();
        if (extraData != null) {
            Object urlAsset = extraData.get("asset_url");
            if (urlAsset != null && !TextUtils.isEmpty(String.valueOf(urlAsset))) {
                return String.valueOf(urlAsset);
            }
            Object urlDirecta = extraData.get("url");
            if (urlDirecta != null && !TextUtils.isEmpty(String.valueOf(urlDirecta))) {
                return String.valueOf(urlDirecta);
            }
        }
        return null;
    }

    // Obtiene un nombre amigable para mostrar el audio.
    private String obtenerNombreAudioAdjunto(Attachment attachment) {
        if (attachment == null) {
            return "Audio";
        }
        if (!TextUtils.isEmpty(attachment.getName())) {
            return attachment.getName();
        }
        if (!TextUtils.isEmpty(attachment.getTitle())) {
            return attachment.getTitle();
        }
        return "Audio";
    }

    // Cierra el dialogo y libera el reproductor del audio.
    private void cerrarReproductorAudio() {
        AlertDialog dialogoActual = audioDialog;
        audioDialog = null;

        if (reproductorAudioDialog != null) {
            try {
                reproductorAudioDialog.release();
            } catch (Exception ignored) {
            }
            reproductorAudioDialog = null;
        }
        if (dialogoActual != null) {
            if (dialogoActual.isShowing()) {
                dialogoActual.dismiss();
            }
        }
    }

    // Muestra u oculta las opciones de grupo segun el tipo de chat.
    private void actualizarVisibilidadOpcionesGrupo() {
        if (botonOpciones == null) {
            return;
        }
        botonOpciones.setVisibility(isGroupChat ? View.VISIBLE : View.GONE);
    }

    // Configura el formateo de fechas de Stream con la zona horaria local.
    private void configurarDateFormatterStream() {
        ChatUI.setDateFormatter(new DateFormatter() {
            @Override
            public String formatDate(Date date) {
                SimpleDateFormat formato = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                formato.setTimeZone(ZONA_HORARIA_STREAM);
                return formato.format(date);
            }

            @Override
            public String formatTime(Date date) {
                SimpleDateFormat formato = new SimpleDateFormat("HH:mm", Locale.getDefault());
                formato.setTimeZone(ZONA_HORARIA_STREAM);
                return formato.format(date);
            }

            @Override
            public String formatRelativeTime(Date date) {
                return formatTime(date);
            }

            @Override
            public String formatRelativeDate(Date date) {
                return formatDate(date);
            }
        });
    }

    @Override
    protected void onDestroy() {
        detenerEscuchaBloqueos();
        cerrarReproductorAudio();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        iniciarEscuchaBloqueoSiAplica();
        if (!creatingNewChannel && !TextUtils.isEmpty(channelId)) {
            cargarInformacionCanalActual();
        }
    }

    // Devuelve el identificador de Stream sin duplicar el prefijo del canal.
    private String obtenerCidStream() {
        if (TextUtils.isEmpty(channelId)) {
            return null;
        }
        if (channelId.contains(":")) {
            return channelId;
        }
        return "messaging:" + channelId;
    }

    // Crea un canal nuevo validando privacidad primero
    private void crearNuevoCanal() {
        indicadorCargaMensajes.setVisibility(View.VISIBLE);
        estadoError.setVisibility(View.GONE);

        privacyService.validarPuedoChateaR(otherUserId, new PrivacyService.PrivacyCheckCallback() {
            @Override
            public void onExito() {
                channelService.obtenerOCrearCanalPrivado(otherUserId, new ChatChannelService.ChannelCallback() {
                    @Override
                    public void onExito(ChatChannelData canal) {
                        channelId = canal.getId();
                        cargarInformacionOtroUsuario();
                        conectarStreamUI();
                    }

                    @Override
                    public void onError(String error) {
                        mostrarError("Error al crear canal: " + error);
                    }
                });
            }

            @Override
            public void onError(String razon) {
                mostrarError(razon);
            }
        });
    }

    // Carga los datos del canal para mostrar nombre y estado en el encabezado
    private void cargarInformacionCanalActual() {
        if (channelId == null) return;

        channelService.obtenerCanal(channelId, new ChatChannelService.ChannelDataCallback() {
            @Override
            public void onExito(ChatChannelData canal) {
                canalActual = canal;
                if (canal.isGroupChat()) {
                    isGroupChat = true;
                    String nombre = canal.getGroupName() == null || canal.getGroupName().trim().isEmpty()
                            ? "Grupo"
                            : canal.getGroupName();
                    nombreOtroUsuario.setText(nombre);
                    estadoOtroUsuario.setText(getString(R.string.grupo_miembros_formato, canal.cantidadMiembros()));
                    mostrarFotoEncabezadoGrupo(canal.getGroupImageUrl(), nombre);
                } else if (TextUtils.isEmpty(otherUserId)) {
                    otherUserId = canal.getOtherUserId(usuarioActualId);
                    iniciarEscuchaBloqueoSiAplica();
                    cargarInformacionOtroUsuario();
                }
            }

            @Override
            public void onError(String error) {
                ToastUtils.showTextToast(ChatViewActivity.this, error, Toast.LENGTH_SHORT);
            }
        });
    }

    // Carga el nick y la foto del otro usuario desde Firestore
    private void cargarInformacionOtroUsuario() {
        if (TextUtils.isEmpty(otherUserId)) return;

        firestore.collection("usuario")
                .document(otherUserId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String nick = doc.getString("Nick");
                    if (nick == null) nick = doc.getString("nick");
                    String fotoPerfil = extraerFotoPerfil(doc.get("FotoPerfil"));
                    nombreOtroUsuario.setText(nick != null ? nick : "Usuario");
                    estadoOtroUsuario.setText("Activo");
                    mostrarFotoEncabezadoUsuario(fotoPerfil);
                });
    }

    // Extrae la foto de perfil cuando viene como texto o como objeto.
    private String extraerFotoPerfil(Object fotoPerfil) {
        if (fotoPerfil == null) {
            return "";
        }
        if (fotoPerfil instanceof String) {
            return String.valueOf(fotoPerfil);
        }
        if (fotoPerfil instanceof Map) {
            Object dataUrl = ((Map<?, ?>) fotoPerfil).get("dataUrl");
            return dataUrl != null ? String.valueOf(dataUrl) : "";
        }
        return "";
    }

    // Muestra la foto del encabezado para chat personal.
    private void mostrarFotoEncabezadoUsuario(String fotoUrl) {
        if (TextUtils.isEmpty(fotoUrl)) {
            fotoOtroUsuario.setImageResource(R.drawable.default_profile_picture);
            return;
        }
        if (fotoUrl.startsWith("data:")) {
            Bitmap bitmapFoto = decodeDataUrl(fotoUrl);
            if (bitmapFoto != null) {
                fotoOtroUsuario.setImageBitmap(bitmapFoto);
            } else {
                fotoOtroUsuario.setImageResource(R.drawable.default_profile_picture);
            }
            return;
        }
        Glide.with(this)
                .load(fotoUrl)
                .placeholder(R.drawable.default_profile_picture)
                .error(R.drawable.default_profile_picture)
                .into(fotoOtroUsuario);
    }

    // Muestra la foto del encabezado para grupo o su inicial si no hay foto.
    private void mostrarFotoEncabezadoGrupo(String fotoUrl, String nombreGrupo) {
        String inicialGrupo = extractGroupInitial(nombreGrupo);
        if (TextUtils.isEmpty(fotoUrl)) {
            if (TextUtils.isEmpty(inicialGrupo)) {
                fotoOtroUsuario.setImageResource(R.drawable.default_profile_picture);
            } else {
                fotoOtroUsuario.setImageBitmap(createGroupInitialBitmap(inicialGrupo));
            }
            return;
        }
        if (fotoUrl.startsWith("data:")) {
            Bitmap bitmapFoto = decodeDataUrl(fotoUrl);
            if (bitmapFoto != null) {
                fotoOtroUsuario.setImageBitmap(bitmapFoto);
            } else if (TextUtils.isEmpty(inicialGrupo)) {
                fotoOtroUsuario.setImageResource(R.drawable.default_profile_picture);
            } else {
                fotoOtroUsuario.setImageBitmap(createGroupInitialBitmap(inicialGrupo));
            }
            return;
        }
        Glide.with(this)
                .load(fotoUrl)
                .placeholder(R.drawable.default_profile_picture)
                .error(R.drawable.default_profile_picture)
                .into(fotoOtroUsuario);
    }

    // Obtiene la inicial del grupo para el avatar.
    private String extractGroupInitial(String nombreGrupo) {
        if (TextUtils.isEmpty(nombreGrupo)) {
            return "";
        }
        String nombreLimpio = nombreGrupo.trim();
        if (TextUtils.isEmpty(nombreLimpio)) {
            return "";
        }
        return nombreLimpio.substring(0, 1).toUpperCase();
    }

    // Crea un avatar circular con inicial para grupos.
    private Bitmap createGroupInitialBitmap(String inicialGrupo) {
        int tamanio = fotoOtroUsuario.getLayoutParams() != null && fotoOtroUsuario.getLayoutParams().width > 0
                ? fotoOtroUsuario.getLayoutParams().width
                : (int) (40f * getResources().getDisplayMetrics().density);
        Bitmap bitmapAvatar = Bitmap.createBitmap(tamanio, tamanio, Bitmap.Config.ARGB_8888);
        Canvas lienzoAvatar = new Canvas(bitmapAvatar);

        Paint pinturaFondo = new Paint(Paint.ANTI_ALIAS_FLAG);
        pinturaFondo.setColor(ContextCompat.getColor(this, R.color.blue_light));
        float radio = tamanio / 2f;
        lienzoAvatar.drawCircle(radio, radio, radio, pinturaFondo);

        Paint pinturaTexto = new Paint(Paint.ANTI_ALIAS_FLAG);
        pinturaTexto.setColor(ContextCompat.getColor(this, R.color.blue));
        pinturaTexto.setTextAlign(Paint.Align.CENTER);
        pinturaTexto.setTypeface(Typeface.DEFAULT_BOLD);
        pinturaTexto.setTextSize(tamanio * 0.46f);

        Paint.FontMetrics metricas = pinturaTexto.getFontMetrics();
        float ejeTextoY = radio - ((metricas.ascent + metricas.descent) / 2f);
        lienzoAvatar.drawText(inicialGrupo, radio, ejeTextoY, pinturaTexto);
        return bitmapAvatar;
    }

    // Convierte un dataUrl en un Bitmap para mostrar la foto
    private Bitmap decodeDataUrl(String dataUrl) {
        if (TextUtils.isEmpty(dataUrl)) return null;
        int indiceComa = dataUrl.indexOf(',');
        if (indiceComa < 0 || indiceComa >= dataUrl.length() - 1) return null;
        try {
            byte[] bytes = Base64.decode(dataUrl.substring(indiceComa + 1), Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    // Abre el perfil del otro usuario si el acceso esta permitido
    private void abrirPerfilOtroUsuario() {
        if (TextUtils.isEmpty(otherUserId)) return;

        FriendshipRepository.canOpenUserProfile(usuarioActualId, otherUserId)
                .addOnSuccessListener(canOpen -> {
                    if (!Boolean.TRUE.equals(canOpen)) {
                        ToastUtils.showTextToast(this, R.string.perfil_acceso_bloqueado, Toast.LENGTH_SHORT);
                        return;
                    }
                    Intent intent = new Intent(this, ProfileActivity.class);
                    intent.putExtra(ProfileActivity.EXTRA_USER_ID, otherUserId);
                    startActivity(intent);
                })
                .addOnFailureListener(e ->
                        ToastUtils.showTextToast(this, R.string.error_perfil_carga, Toast.LENGTH_SHORT)
                );
    }

    // Inicia escucha de bloqueos para desactivar envio en tiempo real.
    private void iniciarEscuchaBloqueoSiAplica() {
        if (isGroupChat || TextUtils.isEmpty(usuarioActualId) || TextUtils.isEmpty(otherUserId)) {
            return;
        }
        detenerEscuchaBloqueos();
        bloqueadoPorMi = false;
        bloqueadoPorOtro = false;
        ultimoEstadoEnvioBloqueado = null;

        escuchadorBloqueoPropio = firestore.collection("usuario")
                .document(usuarioActualId)
                .collection("UsuariosBloqueados")
                .document(otherUserId)
                .addSnapshotListener((doc, error) -> {
                    if (error != null) {
                        return;
                    }
                    bloqueadoPorMi = doc != null && doc.exists();
                    actualizarEstadoBloqueoEnvio();
                });

        escuchadorBloqueoAjeno = firestore.collection("usuario")
                .document(otherUserId)
                .collection("UsuariosBloqueados")
                .document(usuarioActualId)
                .addSnapshotListener((doc, error) -> {
                    if (error != null) {
                        return;
                    }
                    bloqueadoPorOtro = doc != null && doc.exists();
                    actualizarEstadoBloqueoEnvio();
                });
    }

    // Detiene escuchas activas para evitar fugas.
    private void detenerEscuchaBloqueos() {
        if (escuchadorBloqueoPropio != null) {
            escuchadorBloqueoPropio.remove();
            escuchadorBloqueoPropio = null;
        }
        if (escuchadorBloqueoAjeno != null) {
            escuchadorBloqueoAjeno.remove();
            escuchadorBloqueoAjeno = null;
        }
    }

    // Actualiza la UI de envio segun bloqueo entre ambos usuarios.
    private void actualizarEstadoBloqueoEnvio() {
        boolean envioBloqueado = bloqueadoPorMi || bloqueadoPorOtro;
        if (messageComposerView != null) {
            messageComposerView.setVisibility(envioBloqueado ? View.GONE : View.VISIBLE);
        }
        if (!isGroupChat && estadoOtroUsuario != null) {
            if (envioBloqueado) {
                estadoOtroUsuario.setText("No disponible por bloqueo");
            } else {
                estadoOtroUsuario.setText("Activo");
            }
        }

        if (ultimoEstadoEnvioBloqueado == null) {
            ultimoEstadoEnvioBloqueado = envioBloqueado;
            return;
        }
        if (ultimoEstadoEnvioBloqueado == envioBloqueado) {
            return;
        }
        ultimoEstadoEnvioBloqueado = envioBloqueado;
        if (envioBloqueado) {
            ToastUtils.showTextToast(this, "No puedes enviar mensajes por bloqueo entre usuarios", Toast.LENGTH_SHORT);
        } else {
            ToastUtils.showTextToast(this, "Ya puedes enviar mensajes en este chat", Toast.LENGTH_SHORT);
        }
    }

    // Muestra el menu de opciones del canal segun si es grupo o chat privado
    private void abrirOpcionesCanal() {
        if (!isGroupChat || canalActual == null) {
            ToastUtils.showTextToast(this, "Opciones disponibles solo para grupos", Toast.LENGTH_SHORT);
            return;
        }

        boolean esAdmin = canalActual.esAdmin(usuarioActualId);
        List<String> opciones = new ArrayList<>();
        opciones.add(OPCION_INFO_MIEMBROS);
        if (esAdmin) opciones.add(OPCION_BORRAR_GRUPO);
        opciones.add(OPCION_ABANDONAR_GRUPO);
        mostrarDialogoOpcionesGrupo(opciones, esAdmin);
    }

    // Muestra la modal de opciones del grupo con un estilo propio.
    private void mostrarDialogoOpcionesGrupo(List<String> opciones, boolean esAdmin) {
        View vistaDialogo = getLayoutInflater().inflate(R.layout.dialog_group_options, null);
        TextView textoTitulo = vistaDialogo.findViewById(R.id.textoTituloOpcionesGrupo);
        LinearLayout contenedorOpciones = vistaDialogo.findViewById(R.id.contenedorOpcionesGrupo);
        AlertDialog dialogo = new AlertDialog.Builder(this)
                .setView(vistaDialogo)
                .create();

        textoTitulo.setText("Opciones de grupo");

        for (int indice = 0; indice < opciones.size(); indice++) {
            String opcion = opciones.get(indice);
            agregarFilaOpcionGrupo(contenedorOpciones, opcion, () -> ejecutarOpcionGrupo(opcion, esAdmin), dialogo);
            if (indice < opciones.size() - 1) {
                View divisor = new View(this);
                LinearLayout.LayoutParams paramsDivisor = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(1)
                );
                paramsDivisor.topMargin = dpToPx(10);
                paramsDivisor.bottomMargin = dpToPx(10);
                divisor.setLayoutParams(paramsDivisor);
                divisor.setBackgroundColor(ContextCompat.getColor(this, R.color.gray_light));
                contenedorOpciones.addView(divisor);
            }
        }

        dialogo.show();
        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setBackgroundDrawableResource(R.drawable.bg_report_dialog_rounded);
        }
    }

    // Agrega una fila clickeable para cada opcion del grupo.
    private void agregarFilaOpcionGrupo(LinearLayout contenedor, String textoOpcion, Runnable accion, AlertDialog dialogo) {
        TextView filaOpcion = new TextView(this);
        LinearLayout.LayoutParams paramsFila = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        filaOpcion.setLayoutParams(paramsFila);
        filaOpcion.setText(textoOpcion);
        filaOpcion.setTextColor(ContextCompat.getColor(this, R.color.gray_dark));
        filaOpcion.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        filaOpcion.setTypeface(null, Typeface.NORMAL);
        filaOpcion.setPadding(0, dpToPx(4), 0, dpToPx(4));
        filaOpcion.setClickable(true);
        filaOpcion.setFocusable(true);
        filaOpcion.setOnClickListener(v -> {
            dialogo.dismiss();
            accion.run();
        });
        contenedor.addView(filaOpcion);
    }

    // Convierte dp a pixeles para mantener medidas consistentes.
    private int dpToPx(int valorDp) {
        float densidad = getResources().getDisplayMetrics().density;
        return Math.round(valorDp * densidad);
    }

    // Ejecuta la accion de grupo seleccionada por el usuario
    private void ejecutarOpcionGrupo(String opcion, boolean esAdmin) {
        if (OPCION_INFO_MIEMBROS.equals(opcion)) {
            abrirInformacionMiembrosGrupo();
            return;
        }
        if (OPCION_BORRAR_GRUPO.equals(opcion) && esAdmin) {
            confirmarBorradoGrupo();
            return;
        }
        if (OPCION_ABANDONAR_GRUPO.equals(opcion)) {
            confirmarAbandonoGrupo();
        }
    }

    // Pide confirmacion antes de abandonar el grupo.
    private void confirmarAbandonoGrupo() {
        new DeleteConfirmDialogComponent(
                this,
                "Abandonar grupo",
                "Esta accion te sacara del grupo. Deseas continuar?",
                "Cancelar",
                "Abandonar",
                () -> channelService.abandonarGrupo(channelId, new ChatChannelService.OperationCallback() {
                    @Override
                    public void onExito() {
                        ToastUtils.showTextToast(ChatViewActivity.this, "Saliste del grupo", Toast.LENGTH_SHORT);
                        abrirPantallaChatsPrincipal();
                    }

                    @Override
                    public void onError(String error) {
                        ToastUtils.showTextToast(ChatViewActivity.this, obtenerMensajeErrorAbandono(error), Toast.LENGTH_SHORT);
                    }
                })
        ).show();
    }

    // Pide confirmacion antes de borrar el grupo.
    private void confirmarBorradoGrupo() {
        new DeleteConfirmDialogComponent(
                this,
                "Borrar grupo",
                "Esta accion eliminara el grupo para todos los miembros. Deseas continuar?",
                "Cancelar",
                "Borrar",
                () -> channelService.borrarGrupo(channelId, new ChatChannelService.OperationCallback() {
                    @Override
                    public void onExito() {
                        ToastUtils.showTextToast(ChatViewActivity.this, "Grupo eliminado", Toast.LENGTH_SHORT);
                        abrirPantallaChatsPrincipal();
                    }

                    @Override
                    public void onError(String error) {
                        ToastUtils.showTextToast(ChatViewActivity.this, error, Toast.LENGTH_SHORT);
                    }
                })
        ).show();
    }

    // Abre la pantalla de informacion y miembros del grupo.
    private void abrirInformacionMiembrosGrupo() {
        if (!isGroupChat || TextUtils.isEmpty(channelId)) {
            return;
        }
        Intent intent = new Intent(this, GroupMembersActivity.class);
        intent.putExtra("channelId", channelId);
        startActivity(intent);
    }

    // Abre la pantalla principal de chats y limpia la navegacion anterior.
    private void abrirPantallaChatsPrincipal() {
        Intent intent = new Intent(this, ChatsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // Devuelve un mensaje claro cuando no queda admin en el grupo.
    private String obtenerMensajeErrorAbandono(String error) {
        if (error == null) {
            return "No fue posible abandonar el grupo.";
        }
        String errorNormalizado = error.toLowerCase();
        if (errorNormalizado.contains("admin")) {
            return "No fue posible abandonar el grupo. Debe quedar al menos un administrador.";
        }
        return error;
    }

    // Muestra el estado de error y oculta el chat
    private void mostrarError(String mensaje) {
        indicadorCargaMensajes.setVisibility(View.GONE);
        messageListView.setVisibility(View.GONE);
        messageComposerView.setVisibility(View.GONE);
        estadoError.setVisibility(View.VISIBLE);
        ToastUtils.showTextToast(this, mensaje, Toast.LENGTH_SHORT);
    }
}