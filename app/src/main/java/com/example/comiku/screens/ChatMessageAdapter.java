package com.example.comiku.screens;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.comiku.R;
import com.example.comiku.core.error.ErrorHandler;
import com.example.comiku.data.model.ChatMessageData;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// Adaptador para mostrar los mensajes en una conversación con estados visuales
public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.ViewHolder> {
    private final List<ChatMessageData> mensajes;
    private final String usuarioActualId;
    private MessageRetryListener retryListener;
    public interface MessageRetryListener {
        void onRetryMessage(ChatMessageData mensaje);
    }

    public ChatMessageAdapter(List<ChatMessageData> mensajes, String usuarioActualId) {
        this.mensajes = mensajes;
        this.usuarioActualId = usuarioActualId;
    }

    public void setRetryListener(MessageRetryListener listener) {
        this.retryListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatMessageData mensaje = mensajes.get(position);
        holder.bind(mensaje, usuarioActualId, retryListener);
    }

    @Override
    public int getItemCount() {
        return mensajes.size();
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.limpiarAudioSiHaceFalta();
    }

    // ViewHolder para cada elemento de mensaje
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textoMensaje;
        private final TextView horaMensaje;
        private final TextView iconoEstado;
        private final TextView nombreUsuario;
        private final ImageView fotoPerfil;
        private final LinearLayout contenedorEstado;
        private final ImageView imagenAdjunta;
        private final LinearLayout contenedorAudioAdjunto;
        private final TextView textoAudioAdjunto;
        private final Button botonReproducirAudio;
        private String urlImagenAdjuntaActiva;
        private ExoPlayer reproductorAudioLocal;
        private String mensajeAudioActivoIdLocal;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textoMensaje = itemView.findViewById(R.id.textoMensaje);
            horaMensaje = itemView.findViewById(R.id.horaMensaje);
            iconoEstado = itemView.findViewById(R.id.iconoEstado);
            nombreUsuario = itemView.findViewById(R.id.nombreUsuario);
            fotoPerfil = itemView.findViewById(R.id.fotoPerfil);
            contenedorEstado = itemView.findViewById(R.id.contenedorEstado);
            imagenAdjunta = itemView.findViewById(R.id.imagenAdjunta);
            contenedorAudioAdjunto = itemView.findViewById(R.id.contenedorAudioAdjunto);
            textoAudioAdjunto = itemView.findViewById(R.id.textoAudioAdjunto);
            botonReproducirAudio = itemView.findViewById(R.id.botonReproducirAudio);
        }

        public void bind(ChatMessageData mensaje, String usuarioActualId, MessageRetryListener retryListener) {
            // Configurar texto del mensaje y adjunto
            String textoBase = mensaje.getText() == null ? "" : mensaje.getText();
            textoMensaje.setText(textoBase);
            mostrarAdjuntos(mensaje);

            // Configurar hora del mensaje
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            horaMensaje.setText(sdf.format(mensaje.getCreatedAt()));

            // Configurar icono de estado y color
            if (mensaje.isOwn()) {
                String icono = ErrorHandler.obtenerIconoEstado(mensaje.getStatus());
                int color = ErrorHandler.obtenerColorEstado(mensaje.getStatus());

                iconoEstado.setText(icono);
                iconoEstado.setTextColor(color);
                iconoEstado.setVisibility(View.VISIBLE);

                // Si es error, permitir reintentar
                if ("error".equals(mensaje.getStatus()) && retryListener != null) {
                    itemView.setOnClickListener(v -> retryListener.onRetryMessage(mensaje));
                } else {
                    itemView.setOnClickListener(null);
                }
            } else {
                iconoEstado.setVisibility(View.GONE);
                itemView.setOnClickListener(null);
            }

            // Para mensajes del otro usuario, mostrar foto y nombre
            if (!mensaje.isOwn()) {
                fotoPerfil.setVisibility(View.VISIBLE);
                nombreUsuario.setVisibility(View.VISIBLE);
                nombreUsuario.setText("Usuario");
                fotoPerfil.setImageResource(R.drawable.default_profile_picture);
            } else {
                fotoPerfil.setVisibility(View.GONE);
                nombreUsuario.setVisibility(View.GONE);
            }
        }

        // Muestra el adjunto correcto segun el tipo del mensaje.
        private void mostrarAdjuntos(ChatMessageData mensaje) {
            String tipoAdjunto = mensaje.getAttachmentType();
            String urlAdjunto = mensaje.getAttachmentUrl();
            String nombreAdjunto = mensaje.getAttachmentFileName();
            boolean pareceAudioPorTexto = esNombreAudio(mensaje.getText());
            String urlAudioDesdeTexto = extraerUrlDesdeTexto(mensaje.getText());
            if ((urlAdjunto == null || urlAdjunto.trim().isEmpty()) && urlAudioDesdeTexto != null) {
                urlAdjunto = urlAudioDesdeTexto;
                mensaje.setAttachmentUrl(urlAudioDesdeTexto);
            }
            boolean tieneDatosAdjunto = (tipoAdjunto != null && !tipoAdjunto.trim().isEmpty())
                    || (urlAdjunto != null && !urlAdjunto.trim().isEmpty())
                    || (nombreAdjunto != null && !nombreAdjunto.trim().isEmpty());

            imagenAdjunta.setVisibility(View.GONE);
            imagenAdjunta.setOnClickListener(null);
            contenedorAudioAdjunto.setVisibility(View.GONE);
            botonReproducirAudio.setText(R.string.reproducir);
            limpiarAudioSiHaceFalta();
            urlImagenAdjuntaActiva = null;

            if (!tieneDatosAdjunto && !pareceAudioPorTexto) {
                return;
            }

            if ("image".equals(tipoAdjunto) && urlAdjunto != null && !urlAdjunto.trim().isEmpty()) {
                urlImagenAdjuntaActiva = urlAdjunto;
                imagenAdjunta.setVisibility(View.VISIBLE);
                cargarImagenSinRecorte(urlAdjunto);
                imagenAdjunta.setOnClickListener(v -> abrirImagenCompleta());
                return;
            }

            if (esAdjuntoAudio(mensaje) || tieneDatosAdjunto || "file".equals(normalizarTipo(tipoAdjunto))) {
                contenedorAudioAdjunto.setVisibility(View.VISIBLE);
                textoAudioAdjunto.setText(obtenerEtiquetaAudio(mensaje, pareceAudioPorTexto));
                botonReproducirAudio.setEnabled(urlAdjunto != null && !urlAdjunto.trim().isEmpty());
                botonReproducirAudio.setOnClickListener(v -> alternarAudio(mensaje));
                if (mensaje.getText() != null && !mensaje.getText().trim().isEmpty()) {
                    String texto = mensaje.getText().trim();
                    String nombre = mensaje.getAttachmentFileName() == null ? "" : mensaje.getAttachmentFileName().trim();
                    if ((!nombre.isEmpty() && texto.equalsIgnoreCase(nombre)) || esNombreAudio(texto)) {
                        textoMensaje.setText("");
                    }
                }
            }
        }

        // Reproduce o detiene el audio del mensaje actual.
        private void alternarAudio(ChatMessageData mensaje) {
            if (mensaje.getAttachmentUrl() == null || mensaje.getAttachmentUrl().trim().isEmpty()) {
                return;
            }

            if (mensajeAudioActivoIdLocal != null && mensajeAudioActivoIdLocal.equals(mensaje.getId()) && reproductorAudioLocal != null && reproductorAudioLocal.isPlaying()) {
                detenerAudioLocal();
                return;
            }

            detenerAudioLocal();
            reproductorAudioLocal = new ExoPlayer.Builder(itemView.getContext()).build();
            try {
                MediaItem mediaItem = MediaItem.fromUri(mensaje.getAttachmentUrl());
                reproductorAudioLocal.setMediaItem(mediaItem);
                reproductorAudioLocal.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int playbackState) {
                        if (playbackState == Player.STATE_ENDED) {
                            detenerAudioLocal();
                        }
                    }

                    @Override
                    public void onPlayerError(@NonNull androidx.media3.common.PlaybackException error) {
                        detenerAudioLocal();
                    }
                });
                mensajeAudioActivoIdLocal = mensaje.getId();
                botonReproducirAudio.setText("Detener");
                reproductorAudioLocal.prepare();
                reproductorAudioLocal.play();
            } catch (Exception error) {
                detenerAudioLocal();
            }
        }

        // Abre la imagen en una pantalla completa sin recorte.
        private void abrirImagenCompleta() {
            if (urlImagenAdjuntaActiva == null || urlImagenAdjuntaActiva.trim().isEmpty()) {
                return;
            }

            Intent intent = new Intent(itemView.getContext(), ImagePreviewActivity.class);
            intent.putExtra("imageUrl", urlImagenAdjuntaActiva);
            itemView.getContext().startActivity(intent);
        }

        // Carga imagen respetando proporcion para que la burbuja se adapte al contenido.
        private void cargarImagenSinRecorte(String urlAdjunto) {
            int anchoMaximo = convertirDpAPx(280);
            int altoMaximo = convertirDpAPx(360);
            int anchoMinimo = convertirDpAPx(140);
            int altoMinimo = convertirDpAPx(100);

            Glide.with(itemView.getContext())
                    .asBitmap()
                    .load(urlAdjunto)
                    .placeholder(R.drawable.default_profile_picture)
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap recurso, @Nullable Transition<? super Bitmap> transition) {
                            int anchoOriginal = recurso.getWidth();
                            int altoOriginal = recurso.getHeight();
                            if (anchoOriginal <= 0 || altoOriginal <= 0) {
                                aplicarTamanoImagen(anchoMaximo, altoMinimo);
                                imagenAdjunta.setImageBitmap(recurso);
                                return;
                            }

                            float escalaAncho = (float) anchoMaximo / (float) anchoOriginal;
                            float escalaAlto = (float) altoMaximo / (float) altoOriginal;
                            float escalaFinal = Math.min(1f, Math.min(escalaAncho, escalaAlto));
                            int anchoFinal = Math.round(anchoOriginal * escalaFinal);
                            int altoFinal = Math.round(altoOriginal * escalaFinal);

                            if (anchoFinal < anchoMinimo) {
                                float ajuste = (float) anchoMinimo / (float) anchoFinal;
                                anchoFinal = anchoMinimo;
                                altoFinal = Math.min(altoMaximo, Math.round(altoFinal * ajuste));
                            }

                            if (altoFinal < altoMinimo) {
                                altoFinal = altoMinimo;
                            }

                            aplicarTamanoImagen(anchoFinal, altoFinal);
                            imagenAdjunta.setImageBitmap(recurso);
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                            if (placeholder != null) {
                                imagenAdjunta.setImageDrawable(placeholder);
                            }
                        }
                    });
        }

        // Aplica el tamano calculado para que la imagen no se recorte.
        private void aplicarTamanoImagen(int ancho, int alto) {
            ViewGroup.LayoutParams params = imagenAdjunta.getLayoutParams();
            params.width = ancho;
            params.height = alto;
            imagenAdjunta.setLayoutParams(params);
            imagenAdjunta.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }

        // Convierte dp a pixeles segun la densidad del dispositivo.
        private int convertirDpAPx(int dp) {
            float densidad = itemView.getResources().getDisplayMetrics().density;
            return Math.round(dp * densidad);
        }

        // Determina si un adjunto se debe tratar como audio.
        private boolean esAdjuntoAudio(ChatMessageData mensaje) {
            if (mensaje == null) {
                return false;
            }
            String tipo = mensaje.getAttachmentType() == null ? "" : mensaje.getAttachmentType().trim().toLowerCase();
            if ("audio".equals(tipo)) {
                return true;
            }
            if ("file".equals(tipo)) {
                String nombreFile = mensaje.getAttachmentFileName() == null ? "" : mensaje.getAttachmentFileName().trim().toLowerCase();
                String urlFile = mensaje.getAttachmentUrl() == null ? "" : mensaje.getAttachmentUrl().trim().toLowerCase();
                if (esNombreAudio(nombreFile) || esNombreAudio(urlFile)) {
                    return true;
                }
            }
            String nombre = mensaje.getAttachmentFileName() == null ? "" : mensaje.getAttachmentFileName().trim().toLowerCase();
            String url = mensaje.getAttachmentUrl() == null ? "" : mensaje.getAttachmentUrl().trim().toLowerCase();
            if (esNombreAudio(nombre)) {
                return true;
            }
            if (esNombreAudio(url)) {
                return true;
            }
            String texto = mensaje.getText() == null ? "" : mensaje.getText().trim().toLowerCase();
            return esNombreAudio(texto);
        }

        // Detecta si el valor incluye extension de audio soportada.
        private boolean esNombreAudio(String valor) {
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

        // Normaliza tipo nulo para comparaciones simples.
        private String normalizarTipo(String tipo) {
            return tipo == null ? "" : tipo.trim().toLowerCase();
        }

        // Obtiene una etiqueta simple para mostrar el archivo de audio.
        private String obtenerEtiquetaAudio(ChatMessageData mensaje, boolean pareceAudioPorTexto) {
            String nombre = "";
            if (mensaje.getAttachmentFileName() != null && !mensaje.getAttachmentFileName().trim().isEmpty()) {
                nombre = mensaje.getAttachmentFileName().trim();
            } else if (pareceAudioPorTexto && mensaje.getText() != null && !mensaje.getText().trim().isEmpty()) {
                nombre = mensaje.getText().trim();
            }

            if (nombre.isEmpty()) {
                return "Audio";
            }

            String nombreCorto = nombre;
            if (nombreCorto.length() > 24) {
                nombreCorto = nombreCorto.substring(0, 24) + "...";
            }
            return "Audio " + nombreCorto;
        }

        // Extrae una url del texto cuando el mensaje contiene un enlace directo.
        private String extraerUrlDesdeTexto(String textoMensaje) {
            if (textoMensaje == null) {
                return null;
            }
            String texto = textoMensaje.trim();
            if (texto.startsWith("http") && esNombreAudio(texto)) {
                return texto;
            }
            return null;
        }

        // Detiene el audio y libera el reproductor local.
        private void detenerAudioLocal() {
            if (reproductorAudioLocal != null) {
                try {
                    reproductorAudioLocal.release();
                } catch (Exception ignored) {
                }
                reproductorAudioLocal = null;
            }
            mensajeAudioActivoIdLocal = null;
            botonReproducirAudio.setText(R.string.reproducir);
        }

        // Libera el audio cuando la fila ya no se usa.
        private void limpiarAudioSiHaceFalta() {
            detenerAudioLocal();
        }
    }
}
