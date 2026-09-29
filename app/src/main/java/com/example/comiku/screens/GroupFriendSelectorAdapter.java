package com.example.comiku.screens;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.comiku.R;
import com.example.comiku.data.model.UserSearchData;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class GroupFriendSelectorAdapter extends RecyclerView.Adapter<GroupFriendSelectorAdapter.ViewHolder> {
    private final List<UserSearchData> amigos;
    private final Set<String> miembrosSeleccionados;
    private final OnSelectionChangedListener listener;
    private final boolean mostrarNombre;

    public interface OnSelectionChangedListener {
        void onSelectionChanged(Set<String> seleccionActual);
    }

    public GroupFriendSelectorAdapter(List<UserSearchData> amigos, OnSelectionChangedListener listener) {
        this(amigos, true, listener);
    }

    public GroupFriendSelectorAdapter(List<UserSearchData> amigos, boolean mostrarNombre, OnSelectionChangedListener listener) {
        this.amigos = amigos;
        this.listener = listener;
        this.mostrarNombre = mostrarNombre;
        this.miembrosSeleccionados = new HashSet<>();
    }


    public Set<String> obtenerSeleccionados() {
        return new HashSet<>(miembrosSeleccionados);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_group_friend_selector, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(amigos.get(position));
    }

    @Override
    public int getItemCount() {
        return amigos.size();
    }


    public class ViewHolder extends RecyclerView.ViewHolder {
        private final RoundedImageView fotoPerfil;
        private final TextView nick;
        private final CheckBox checkSeleccionado;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            fotoPerfil = itemView.findViewById(R.id.fotoPerfil);
            nick = itemView.findViewById(R.id.nick);
            checkSeleccionado = itemView.findViewById(R.id.checkSeleccionado);
            fotoPerfil.setCircular(true);
        }

        // Vincular amigo y estado de seleccion
        public void bind(UserSearchData amigo) {
        nick.setText(amigo.nick != null && !amigo.nick.isEmpty() ? amigo.nick : "Usuario");
        if (!TextUtils.isEmpty(amigo.fotoPerfilDataUrl) && !amigo.fotoPerfilDataUrl.startsWith("data:")) {
            Glide.with(fotoPerfil.getContext())
                    .load(amigo.fotoPerfilDataUrl)
                        .placeholder(R.drawable.default_profile_picture)
                        .error(R.drawable.default_profile_picture)
                        .circleCrop()
                        .into(fotoPerfil);
            } else {
                Bitmap bitmapFoto = decodeDataUrl(amigo.fotoPerfilDataUrl);
                if (bitmapFoto != null) {
                    fotoPerfil.setImageBitmap(bitmapFoto);
                } else {
                    fotoPerfil.setImageResource(R.drawable.default_profile_picture);
                }
            }

            checkSeleccionado.setOnCheckedChangeListener(null);
            checkSeleccionado.setChecked(miembrosSeleccionados.contains(amigo.uid));
            checkSeleccionado.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    miembrosSeleccionados.add(amigo.uid);
                } else {
                    miembrosSeleccionados.remove(amigo.uid);
                }
                listener.onSelectionChanged(new HashSet<>(miembrosSeleccionados));
            });

            itemView.setOnClickListener(v -> checkSeleccionado.setChecked(!checkSeleccionado.isChecked()));
        }

        // Convierte una dataUrl en imagen para mostrarla en la lista.
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
}
