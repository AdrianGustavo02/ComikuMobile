package com.example.comiku.screens;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.comiku.R;
import com.example.comiku.data.model.UserSearchData;
import java.util.List;


public class FriendSelectorAdapter extends RecyclerView.Adapter<FriendSelectorAdapter.ViewHolder> {
    private final List<UserSearchData> amigos;
    private final OnFriendClickListener listener;

    public interface OnFriendClickListener {
        void onFriendClick(UserSearchData amigo);
    }

    public FriendSelectorAdapter(List<UserSearchData> amigos, OnFriendClickListener listener) {
        this.amigos = amigos;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_friend_selector, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserSearchData amigo = amigos.get(position);
        holder.bind(amigo, listener);
    }

    @Override
    public int getItemCount() {
        return amigos.size();
    }


    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView fotoPerfil;
        private final TextView nick;
        private final TextView nombre;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            fotoPerfil = itemView.findViewById(R.id.fotoPerfil);
            nick = itemView.findViewById(R.id.nick);
            nombre = itemView.findViewById(R.id.nombre);
        }

        public void bind(UserSearchData amigo, OnFriendClickListener listener) {
            // Configurar nick
            if (amigo.nick != null && !amigo.nick.isEmpty()) {
                nick.setText(amigo.nick);
            } else {
                nick.setText("Usuario sin nick");
            }

            // Ocultar nombre para mostrar solo el nick.
            nombre.setVisibility(View.GONE);

            // Configurar foto de perfil real o usar una imagen por defecto.
            Bitmap bitmapFoto = decodeDataUrl(amigo.fotoPerfilDataUrl);
            if (bitmapFoto != null) {
                fotoPerfil.setImageBitmap(bitmapFoto);
            } else {
                fotoPerfil.setImageResource(R.drawable.default_profile_picture);
            }

            // Configurar click listener
            itemView.setOnClickListener(v -> listener.onFriendClick(amigo));
        }

        // Convierte una imagen en base64 para mostrarla en la lista.
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
