package com.example.comiku.screens;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.comiku.R;
import com.example.comiku.data.model.ChatChannelData;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Adaptador para mostrar los canales de chat en una lista
public class ChatChannelAdapter extends RecyclerView.Adapter<ChatChannelAdapter.ViewHolder> {
    private final List<ChatChannelData> canales;
    private final OnChannelClickListener listener;
    private final FirebaseFirestore firestore;
    private final String usuarioActualId;
    private final Map<String, String> cacheFotoUsuarios = new HashMap<>();
    private final Map<String, String> cacheFotoGrupos = new HashMap<>();

    public interface OnChannelClickListener {
        void onChannelClick(ChatChannelData canal);
    }

    public ChatChannelAdapter(List<ChatChannelData> canales, String usuarioActualId, OnChannelClickListener listener) {
        this.canales = canales;
        this.usuarioActualId = usuarioActualId;
        this.listener = listener;
        this.firestore = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_channel, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatChannelData canal = canales.get(position);
        holder.bind(canal, usuarioActualId, listener);
        cargarFotoCanal(canal, holder);
    }

    @Override
    public int getItemCount() {
        return canales.size();
    }

    // ViewHolder para cada elemento de la lista
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView fotoPerfil;
        private final TextView nombreChat;
        private final TextView ultimoMensaje;
        private final TextView horaRelativa;
        private final TextView contadorNoLeidos;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            fotoPerfil = itemView.findViewById(R.id.fotoPerfil);
            nombreChat = itemView.findViewById(R.id.nombreChat);
            ultimoMensaje = itemView.findViewById(R.id.ultimoMensaje);
            horaRelativa = itemView.findViewById(R.id.horaRelativa);
            contadorNoLeidos = itemView.findViewById(R.id.contadorNoLeidos);
        }

        public void bind(ChatChannelData canal, String usuarioActualId, OnChannelClickListener listener) {
            // Configurar nombre del chat
            if (canal.isGroupChat()) {
                // Para grupos, mostrar el nombre del grupo
                nombreChat.setText(canal.getGroupName() != null ? canal.getGroupName() : "Grupo");
            } else {
                // Para chats 1:1, mostrar nick resuelto o fallback simple
                String nombreVisible = canal.getDisplayName();
                if (nombreVisible == null || nombreVisible.trim().isEmpty()) {
                    nombreVisible = "Chat personal";
                }
                nombreChat.setText(nombreVisible);
            }

            // Configurar último mensaje
            if (canal.getLastMessage() != null) {
                ultimoMensaje.setText(canal.getLastMessage());
                ultimoMensaje.setVisibility(View.VISIBLE);
            } else {
                ultimoMensaje.setVisibility(View.GONE);
            }

            // Configurar hora relativa
            if (canal.getLastMessageAt() > 0) {
                String horaFormato = obtenerHoraRelativa(canal.getLastMessageAt());
                horaRelativa.setText(horaFormato);
            }

            // Configurar contador de no leídos
            if (canal.getUnreadCount() > 0) {
                contadorNoLeidos.setVisibility(View.VISIBLE);
                contadorNoLeidos.setText(String.valueOf(canal.getUnreadCount()));
            } else {
                contadorNoLeidos.setVisibility(View.GONE);
            }

            // Muestra placeholder mientras llega la foto real.
            fotoPerfil.setImageResource(R.drawable.default_profile_picture);
            fotoPerfil.setTag(obtenerClaveVisual(canal, usuarioActualId));

            // Configurar click listener
            itemView.setOnClickListener(v -> listener.onChannelClick(canal));
        }

        // Devuelve una clave visual estable para evitar mezclar imagenes por reciclado.
        private String obtenerClaveVisual(ChatChannelData canal, String usuarioActualId) {
            if (canal == null) {
                return "";
            }
            if (canal.isGroupChat()) {
                return "group:" + (canal.getId() == null ? "" : canal.getId());
            }
            String otroId = canal.getOtherUserId(usuarioActualId);
            return "user:" + (otroId == null ? "" : otroId);
        }

        // Obtener hora relativa en formato legible
        private String obtenerHoraRelativa(long timestamp) {
            long ahora = System.currentTimeMillis();
            long diferencia = ahora - timestamp;

            // Milisegundos en diferentes unidades
            long minutos = diferencia / (60 * 1000);
            long horas = diferencia / (60 * 60 * 1000);
            long dias = diferencia / (24 * 60 * 60 * 1000);

            if (minutos < 1) {
                return "Hace poco";
            } else if (minutos < 60) {
                return "Hace " + minutos + " min";
            } else if (horas < 24) {
                return "Hace " + horas + " h";
            } else if (dias < 7) {
                return "Hace " + dias + " d";
            } else {
                // Mostrar fecha formateada
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                return sdf.format(new Date(timestamp));
            }
        }
    }

    // Resuelve y muestra la foto del canal segun su tipo.
    private void cargarFotoCanal(ChatChannelData canal, ViewHolder holder) {
        if (canal == null || holder == null) {
            return;
        }
        if (canal.isGroupChat()) {
            cargarFotoGrupo(canal, holder);
        } else {
            cargarFotoUsuario(canal, holder);
        }
    }

    // Carga la foto del grupo desde cache o desde los datos del canal.
    private void cargarFotoGrupo(ChatChannelData canal, ViewHolder holder) {
        String canalId = canal.getId() == null ? "" : canal.getId();
        String clave = "group:" + canalId;
        String fotoCanal = canal.getGroupImageUrl() == null ? "" : canal.getGroupImageUrl();
        String fotoCache = cacheFotoGrupos.get(clave);
        if (!fotoCanal.equals(fotoCache)) {
            if (TextUtils.isEmpty(fotoCanal)) {
                cacheFotoGrupos.remove(clave);
            } else {
                cacheFotoGrupos.put(clave, fotoCanal);
            }
        }
        aplicarImagenGrupo(holder.fotoPerfil, fotoCanal, canal.getGroupName(), clave);
    }

    // Carga la foto del otro usuario en un chat personal.
    private void cargarFotoUsuario(ChatChannelData canal, ViewHolder holder) {
        String otroId = canal.getOtherUserId(usuarioActualId);
        if (TextUtils.isEmpty(otroId)) {
            return;
        }
        String clave = "user:" + otroId;
        String fotoCacheada = cacheFotoUsuarios.get(clave);
        if (!TextUtils.isEmpty(fotoCacheada)) {
            aplicarImagen(holder.fotoPerfil, fotoCacheada, clave);
            return;
        }

        holder.fotoPerfil.setTag(clave);
        firestore.collection("usuario")
                .document(otroId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        return;
                    }
                    String foto = extraerFotoPerfil(doc.get("FotoPerfil"));
                    cacheFotoUsuarios.put(clave, foto);
                    aplicarImagen(holder.fotoPerfil, foto, clave);
                });
    }

    // Aplica una imagen en el ImageView respetando el reciclado de filas.
    private void aplicarImagen(ImageView vista, String foto, String claveEsperada) {
        if (vista == null) {
            return;
        }
        Object tagActual = vista.getTag();
        if (tagActual == null || !String.valueOf(tagActual).equals(claveEsperada)) {
            return;
        }
        if (TextUtils.isEmpty(foto)) {
            vista.setImageResource(R.drawable.default_profile_picture);
            return;
        }

        if (foto.startsWith("data:")) {
            Bitmap bitmap = decodeDataUrl(foto);
            if (bitmap != null) {
                vista.setImageBitmap(bitmap);
            } else {
                vista.setImageResource(R.drawable.default_profile_picture);
            }
            return;
        }

        Glide.with(vista.getContext())
                .load(foto)
                .placeholder(R.drawable.default_profile_picture)
                .error(R.drawable.default_profile_picture)
                .into(vista);
    }

    // Aplica imagen de grupo y usa inicial cuando no hay foto cargada.
    private void aplicarImagenGrupo(ImageView vista, String foto, String nombreGrupo, String claveEsperada) {
        if (vista == null) {
            return;
        }
        Object tagActual = vista.getTag();
        if (tagActual == null || !String.valueOf(tagActual).equals(claveEsperada)) {
            return;
        }

        String inicialGrupo = extractGroupInitial(nombreGrupo);
        if (TextUtils.isEmpty(foto)) {
            if (TextUtils.isEmpty(inicialGrupo)) {
                vista.setImageResource(R.drawable.default_profile_picture);
            } else {
                vista.setImageBitmap(createGroupInitialBitmap(vista, inicialGrupo));
            }
            return;
        }

        if (foto.startsWith("data:")) {
            Bitmap bitmap = decodeDataUrl(foto);
            if (bitmap != null) {
                vista.setImageBitmap(bitmap);
            } else if (TextUtils.isEmpty(inicialGrupo)) {
                vista.setImageResource(R.drawable.default_profile_picture);
            } else {
                vista.setImageBitmap(createGroupInitialBitmap(vista, inicialGrupo));
            }
            return;
        }

        Glide.with(vista.getContext())
                .load(foto)
                .placeholder(R.drawable.default_profile_picture)
                .error(R.drawable.default_profile_picture)
                .into(vista);
    }

    // Obtiene la inicial del nombre para avatar de grupo.
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

    // Crea un avatar circular con inicial del grupo.
    private Bitmap createGroupInitialBitmap(ImageView vista, String inicial) {
        int tamanio = vista.getLayoutParams() != null && vista.getLayoutParams().width > 0
                ? vista.getLayoutParams().width
                : (int) (44f * vista.getResources().getDisplayMetrics().density);
        Bitmap bitmapAvatar = Bitmap.createBitmap(tamanio, tamanio, Bitmap.Config.ARGB_8888);
        Canvas lienzoAvatar = new Canvas(bitmapAvatar);

        Paint pinturaFondo = new Paint(Paint.ANTI_ALIAS_FLAG);
        pinturaFondo.setColor(ContextCompat.getColor(vista.getContext(), R.color.blue_light));
        float radio = tamanio / 2f;
        lienzoAvatar.drawCircle(radio, radio, radio, pinturaFondo);

        Paint pinturaTexto = new Paint(Paint.ANTI_ALIAS_FLAG);
        pinturaTexto.setColor(ContextCompat.getColor(vista.getContext(), R.color.blue));
        pinturaTexto.setTextAlign(Paint.Align.CENTER);
        pinturaTexto.setTypeface(Typeface.DEFAULT_BOLD);
        pinturaTexto.setTextSize(tamanio * 0.46f);

        Paint.FontMetrics metricas = pinturaTexto.getFontMetrics();
        float ejeTextoY = radio - ((metricas.ascent + metricas.descent) / 2f);
        lienzoAvatar.drawText(inicial, radio, ejeTextoY, pinturaTexto);
        return bitmapAvatar;
    }

    // Extrae la foto de perfil cuando viene como objeto o como texto.
    private String extraerFotoPerfil(Object fotoPerfil) {
        if (fotoPerfil == null) {
            return "";
        }
        if (fotoPerfil instanceof String) {
            return String.valueOf(fotoPerfil);
        }
        if (fotoPerfil instanceof Map) {
            Object dataUrl = ((Map<?, ?>) fotoPerfil).get("dataUrl");
            return dataUrl == null ? "" : String.valueOf(dataUrl);
        }
        return "";
    }

    // Convierte un dataUrl en bitmap para pintar imagenes locales.
    private Bitmap decodeDataUrl(String dataUrl) {
        if (TextUtils.isEmpty(dataUrl)) {
            return null;
        }
        int indiceComa = dataUrl.indexOf(',');
        if (indiceComa < 0 || indiceComa >= dataUrl.length() - 1) {
            return null;
        }
        try {
            byte[] bytes = Base64.decode(dataUrl.substring(indiceComa + 1), Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }
}
