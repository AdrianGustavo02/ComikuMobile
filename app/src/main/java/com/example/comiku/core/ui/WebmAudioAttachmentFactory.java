package com.example.comiku.core.ui;

import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.media.MediaMetadataRetriever;

import java.util.ArrayList;
import java.util.List;

import io.getstream.chat.android.client.ChatClient;
import io.getstream.chat.android.client.audio.AudioPlayer;
import io.getstream.chat.android.client.audio.AudioState;
import io.getstream.chat.android.client.audio.ProgressData;
import io.getstream.chat.android.models.Attachment;
import io.getstream.chat.android.models.AttachmentType;
import io.getstream.chat.android.models.Message;
import io.getstream.chat.android.ui.feature.messages.list.adapter.MessageListListenerContainer;
import io.getstream.chat.android.ui.feature.messages.list.adapter.MessageListListeners;
import io.getstream.chat.android.ui.feature.messages.list.adapter.view.internal.AudioRecordPlayerView;
import io.getstream.chat.android.ui.feature.messages.list.adapter.viewholder.attachment.AttachmentFactory;
import io.getstream.chat.android.ui.feature.messages.list.adapter.viewholder.attachment.InnerAttachmentViewHolder;
import kotlin.Unit;

// Muestra audios recibidos con el reproductor nativo de Stream y su onda visual.
public class WebmAudioAttachmentFactory implements AttachmentFactory {

    // Indica si el mensaje trae al menos un adjunto que se debe mostrar como audio.
    @Override
    public boolean canHandle(Message message) {
        if (message == null || message.getAttachments() == null || message.getAttachments().isEmpty()) {
            return false;
        }

        for (Attachment attachment : message.getAttachments()) {
            if (esAudioAdjunto(attachment)) {
                return true;
            }
        }
        return false;
    }

    // Crea la vista nativa de audio y la prepara para reproducir archivos webm.
    @Override
    public InnerAttachmentViewHolder createViewHolder(Message message, MessageListListenerContainer listeners, ViewGroup parent) {
        return crearHolderAudio(parent);
    }

    // Puente para la firma nueva de Stream.
    @Override
    public InnerAttachmentViewHolder createViewHolder(Message message, MessageListListeners listeners, ViewGroup parent) {
        return crearHolderAudio(parent);
    }

    // Crea un holder que dibuja uno o mas reproductores nativos de audio.
    private InnerAttachmentViewHolder crearHolderAudio(ViewGroup parent) {
        LinearLayout contenedor = new LinearLayout(parent.getContext());
        contenedor.setOrientation(LinearLayout.VERTICAL);
        return new InnerAttachmentViewHolder(contenedor) {
            private final List<Integer> hashesAudio = new ArrayList<>();

            @Override
            public void onBindViewHolder(Message message) {
                contenedor.removeAllViews();
                hashesAudio.clear();

                List<Attachment> adjuntosAudio = convertirAdjuntosAudio(message);
                if (adjuntosAudio.isEmpty()) {
                    return;
                }

                AudioPlayer audioPlayer = ChatClient.instance().getAudioPlayer();
                for (int indice = 0; indice < adjuntosAudio.size(); indice++) {
                    Attachment attachment = adjuntosAudio.get(indice);
                    AudioRecordPlayerView playerView = new AudioRecordPlayerView(contenedor.getContext());
                    configurarPlayer(playerView, attachment, indice, audioPlayer, hashesAudio);
                    contenedor.addView(playerView);
                    if (indice > 0) {
                        ViewGroup.LayoutParams params = playerView.getLayoutParams();
                        if (params instanceof LinearLayout.LayoutParams) {
                            ((LinearLayout.LayoutParams) params).topMargin = dpToPx(2, contenedor);
                            playerView.setLayoutParams(params);
                        }
                    }
                }
            }

            @Override
            public void onUnbindViewHolder() {
                if (!hashesAudio.isEmpty()) {
                    ChatClient.instance().getAudioPlayer().removeAudios(new ArrayList<>(hashesAudio));
                    hashesAudio.clear();
                }
                contenedor.removeAllViews();
            }
        };
    }

    // Configura el reproductor para que se vea y funcione como audio nativo.
    private void configurarPlayer(AudioRecordPlayerView playerView, Attachment attachment, int posicion, AudioPlayer audioPlayer, List<Integer> hashesAudio) {
        String urlAudio = obtenerUrlAudio(attachment);
        int audioHash = urlAudio.hashCode() ^ posicion;
        playerView.setTotalDuration("00:00");
        playerView.setWaveBars(generarWaveform(attachment));
        playerView.setTag(audioHash);
        cargarDuracionReal(playerView, urlAudio, attachment);

        registrarEstadoAudio(playerView, audioPlayer, audioHash);

        playerView.setOnPlayButtonClickListener(() -> {
            if (urlAudio == null || urlAudio.trim().isEmpty()) {
                playerView.setLoading();
                return Unit.INSTANCE;
            }
            audioPlayer.clearTracks();
            audioPlayer.registerTrack(urlAudio, audioHash, posicion);
            audioPlayer.play(urlAudio, audioHash);
            return Unit.INSTANCE;
        });

        playerView.setOnSpeedButtonClickListener(() -> {
            audioPlayer.changeSpeed();
            return Unit.INSTANCE;
        });

        playerView.setOnSeekbarMoveListeners(() -> {
            audioPlayer.startSeek(audioHash);
            return Unit.INSTANCE;
        }, progress -> {
            int durationMs = obtenerDuracionEnMs(attachment);
            if (durationMs <= 0) {
                durationMs = 1000;
            }
            int posicionMs = (int) ((progress / 100f) * durationMs);
            audioPlayer.seekTo(posicionMs, audioHash);
            return Unit.INSTANCE;
        });

        hashesAudio.add(audioHash);
    }

    // Registra el estado del audio para que el reproductor cambie su aspecto.
    private void registrarEstadoAudio(AudioRecordPlayerView playerView, AudioPlayer audioPlayer, int audioHash) {
        audioPlayer.registerOnAudioStateChange(audioHash, estado -> {
            if (estado == AudioState.LOADING) {
                playerView.setLoading();
            } else if (estado == AudioState.PLAYING) {
                playerView.setPlaying();
            } else if (estado == AudioState.PAUSE) {
                playerView.setPaused();
            } else {
                playerView.setIdle();
            }
            return Unit.INSTANCE;
        });

        audioPlayer.registerOnProgressStateChange(audioHash, progreso -> {
            playerView.setDuration(formatearDuracionMs(progreso.getDuration()));
            playerView.setProgress(progreso.getProgress());
            return Unit.INSTANCE;
        });

        audioPlayer.registerOnSpeedChange(audioHash, speed -> {
            playerView.setSpeedText(speed);
            return Unit.INSTANCE;
        });
    }

    // Convierte los adjuntos de audio al tipo que usa el reproductor nativo de Stream.
    private List<Attachment> convertirAdjuntosAudio(Message message) {
        List<Attachment> adjuntosAudio = new ArrayList<>();
        if (message == null || message.getAttachments() == null) {
            return adjuntosAudio;
        }

        for (Attachment attachment : message.getAttachments()) {
            if (!esAudioAdjunto(attachment)) {
                continue;
            }
            Attachment adjuntoAudio = new Attachment.Builder()
                    .withType(AttachmentType.AUDIO_RECORDING)
                    .withAssetUrl(obtenerUrlAudio(attachment))
                    .withMimeType(obtenerMimeTypeAudio(attachment))
                    .withName(obtenerNombreAudio(attachment))
                    .withTitle(obtenerTituloAudio(attachment))
                    .build();
            adjuntosAudio.add(adjuntoAudio);
        }
        return adjuntosAudio;
    }

    // Indica si un adjunto corresponde a un audio webm o a otro formato de audio.
    private boolean esAudioAdjunto(Attachment attachment) {
        if (attachment == null) {
            return false;
        }

        String tipo = valorNormalizado(attachment.getType());
        if (AttachmentType.AUDIO.equals(tipo) || AttachmentType.AUDIO_RECORDING.equals(tipo)) {
            return true;
        }

        String mime = valorNormalizado(attachment.getMimeType());
        if (mime.startsWith("audio/")) {
            return true;
        }

        String nombre = valorNormalizado(attachment.getName());
        String titulo = valorNormalizado(attachment.getTitle());
        String assetUrl = valorNormalizado(attachment.getAssetUrl());
        String imageUrl = valorNormalizado(attachment.getImageUrl());

        return esRutaAudio(nombre)
                || esRutaAudio(titulo)
                || esRutaAudio(assetUrl)
                || esRutaAudio(imageUrl);
    }

    // Obtiene la url util para reproducir el audio.
    private String obtenerUrlAudio(Attachment attachment) {
        if (attachment == null) {
            return "";
        }

        if (!isEmpty(attachment.getAssetUrl())) {
            return attachment.getAssetUrl();
        }
        if (!isEmpty(attachment.getImageUrl())) {
            return attachment.getImageUrl();
        }
        return "";
    }

    // Obtiene el mime type para mantener el audio compatible con Stream.
    private String obtenerMimeTypeAudio(Attachment attachment) {
        if (attachment == null || isEmpty(attachment.getMimeType())) {
            return "audio/webm";
        }
        return attachment.getMimeType();
    }

    // Obtiene el nombre para mostrar en el reproductor.
    private String obtenerNombreAudio(Attachment attachment) {
        if (attachment == null) {
            return "audio";
        }
        if (!isEmpty(attachment.getName())) {
            return attachment.getName();
        }
        if (!isEmpty(attachment.getTitle())) {
            return attachment.getTitle();
        }
        return "audio";
    }

    // Obtiene el titulo para mostrar en el reproductor.
    private String obtenerTituloAudio(Attachment attachment) {
        if (attachment == null) {
            return "audio";
        }
        if (!isEmpty(attachment.getTitle())) {
            return attachment.getTitle();
        }
        if (!isEmpty(attachment.getName())) {
            return attachment.getName();
        }
        return "audio";
    }

    // Genera una forma de onda simple para que el reproductor muestre la pista visual.
    private List<Float> generarWaveform(Attachment attachment) {
        List<Float> onda = new ArrayList<>();
        String semilla = obtenerUrlAudio(attachment);
        if (isEmpty(semilla)) {
            semilla = obtenerNombreAudio(attachment);
        }
        int base = Math.abs(semilla.hashCode());
        for (int i = 0; i < 40; i++) {
            int valor = (base >> (i % 16)) ^ (i * 31);
            float altura = 0.2f + (Math.abs(valor) % 80) / 100f;
            if (altura > 1f) {
                altura = 1f;
            }
            onda.add(altura);
        }
        return onda;
    }

    // Convierte la duracion de StreamChat a un texto simple.
    private String formatearDuracion(Attachment attachment) {
        int duracionMs = obtenerDuracionEnMs(attachment);
        if (duracionMs <= 0) {
            duracionMs = 0;
        }
        return formatearDuracionMs(duracionMs);
    }

    // Convierte una duracion en milisegundos a formato mm:ss.
    private String formatearDuracionMs(int duracionMs) {
        if (duracionMs <= 0) {
            duracionMs = 12000;
        }
        int segundosTotales = duracionMs / 1000;
        int minutos = segundosTotales / 60;
        int segundos = segundosTotales % 60;
        return String.format("%02d:%02d", minutos, segundos);
    }

    // Obtiene la duracion en milisegundos si esta disponible.
    private int obtenerDuracionEnMs(Attachment attachment) {
        if (attachment == null || attachment.getExtraData() == null) {
            return 0;
        }
        Object duracion = attachment.getExtraData().get("duration");
        if (duracion instanceof Number) {
            double valor = ((Number) duracion).doubleValue();
            if (valor > 1000d) {
                return (int) Math.round(valor);
            }
            return (int) Math.round(valor * 1000d);
        }
        Object duracionMs = attachment.getExtraData().get("duration_ms");
        if (duracionMs instanceof Number) {
            return ((Number) duracionMs).intValue();
        }
        return 0;
    }

    // Lee la duracion real del archivo para no depender de valores de respaldo.
    private void cargarDuracionReal(AudioRecordPlayerView playerView, String urlAudio, Attachment attachment) {
        final String urlValida = urlAudio == null ? "" : urlAudio.trim();
        if (urlValida.isEmpty()) {
            return;
        }

        new Thread(() -> {
            int duracionMs = obtenerDuracionDesdeArchivo(urlValida);
            if (duracionMs <= 0) {
                duracionMs = obtenerDuracionEnMs(attachment);
            }
            if (duracionMs <= 0) {
                return;
            }
            final String textoDuracion = formatearDuracionMs(duracionMs);
            playerView.post(() -> {
                if (playerView.getTag() != null) {
                    playerView.setTotalDuration(textoDuracion);
                }
            });
        }).start();
    }

    // Extrae la duracion del audio desde la url usando el metadato del archivo.
    private int obtenerDuracionDesdeArchivo(String urlAudio) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(urlAudio, new java.util.HashMap<String, String>());
            String duracion = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (duracion == null || duracion.trim().isEmpty()) {
                return 0;
            }
            return Integer.parseInt(duracion.trim());
        } catch (Exception error) {
            return 0;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    // Verifica si una ruta parece corresponder a un archivo de audio.
    private boolean esRutaAudio(String valor) {
        if (isEmpty(valor)) {
            return false;
        }

        String texto = valor.trim().toLowerCase();
        return texto.contains(".webm")
                || texto.contains(".ogg")
                || texto.contains(".m4a")
                || texto.contains(".aac")
                || texto.contains(".mp3")
                || texto.contains(".wav");
    }

    // Convierte dp a pixeles de forma simple.
    private int dpToPx(int dp, ViewGroup parent) {
        float densidad = parent.getResources().getDisplayMetrics().density;
        return Math.round(dp * densidad);
    }

    // Normaliza un valor para compararlo sin problemas.
    private String valorNormalizado(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase();
    }

    // Indica si una cadena esta vacia.
    private boolean isEmpty(String valor) {
        return valor == null || valor.trim().isEmpty();
    }
}
