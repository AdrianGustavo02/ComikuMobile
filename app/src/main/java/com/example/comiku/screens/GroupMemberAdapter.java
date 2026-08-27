package com.example.comiku.screens;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.bumptech.glide.Glide;
import androidx.recyclerview.widget.RecyclerView;
import com.example.comiku.R;
import com.example.comiku.data.model.GroupMemberData;
import java.util.Map;
import java.util.List;


public class GroupMemberAdapter extends RecyclerView.Adapter<GroupMemberAdapter.ViewHolder> {
    private final List<GroupMemberData> miembros;
    private final boolean usuarioEsAdmin;
    private final String usuarioActualId;
    private final MemberActionListener listener;

    public interface MemberActionListener {
        void onPromote(GroupMemberData miembro);
        void onRemove(GroupMemberData miembro);
        void onOpenProfile(GroupMemberData miembro);
    }

    public GroupMemberAdapter(List<GroupMemberData> miembros, boolean usuarioEsAdmin, String usuarioActualId,
                              MemberActionListener listener) {
        this.miembros = miembros;
        this.usuarioEsAdmin = usuarioEsAdmin;
        this.usuarioActualId = usuarioActualId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group_member, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(miembros.get(position));
    }

    @Override
    public int getItemCount() {
        return miembros.size();
    }


    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imagenPerfil;
        private final TextView textoNick;
        private final TextView badgeAdmin;
        private final ImageButton botonOpciones;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imagenPerfil = itemView.findViewById(R.id.imagenMiembroPerfil);
            textoNick = itemView.findViewById(R.id.textoNickMiembro);
            badgeAdmin = itemView.findViewById(R.id.badgeAdminMiembro);
            botonOpciones = itemView.findViewById(R.id.botonOpcionesMiembro);
        }

        // Vincular datos del miembro y permisos de accion
        public void bind(GroupMemberData miembro) {
            textoNick.setText(miembro.getNick() == null || miembro.getNick().isEmpty() ? "Usuario" : miembro.getNick());
            cargarFotoPerfil(miembro.getFotoPerfil());
            badgeAdmin.setVisibility(miembro.isAdmin() ? View.VISIBLE : View.GONE);

            boolean puedeGestionar = usuarioEsAdmin && !miembro.getUid().equals(usuarioActualId);
            botonOpciones.setVisibility(puedeGestionar ? View.VISIBLE : View.GONE);

            textoNick.setOnClickListener(v -> listener.onOpenProfile(miembro));
            imagenPerfil.setOnClickListener(v -> listener.onOpenProfile(miembro));
            botonOpciones.setOnClickListener(v -> mostrarMenuAcciones(miembro));
        }

        // Carga la foto del miembro
        private void cargarFotoPerfil(String fotoPerfil) {
            if (TextUtils.isEmpty(fotoPerfil)) {
                imagenPerfil.setImageResource(R.drawable.default_profile_picture);
                return;
            }
            if (fotoPerfil.startsWith("data:")) {
                Bitmap bitmapPerfil = decodeDataUrl(fotoPerfil);
                if (bitmapPerfil != null) {
                    imagenPerfil.setImageBitmap(bitmapPerfil);
                } else {
                    imagenPerfil.setImageResource(R.drawable.default_profile_picture);
                }
                return;
            }
            Glide.with(imagenPerfil.getContext())
                    .load(fotoPerfil)
                    .placeholder(R.drawable.default_profile_picture)
                    .error(R.drawable.default_profile_picture)
                    .circleCrop()
                    .into(imagenPerfil);
        }

        // Muestra las acciones del miembro
        private void mostrarMenuAcciones(GroupMemberData miembro) {
            PopupMenu menuOpciones = new PopupMenu(itemView.getContext(), botonOpciones);
            if (!miembro.isAdmin()) {
                menuOpciones.getMenu().add(Menu.NONE, 1, 1, "Hacer admin");
            }
            menuOpciones.getMenu().add(Menu.NONE, 2, 2, "Eliminar del grupo");
            menuOpciones.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    listener.onPromote(miembro);
                    return true;
                }
                if (item.getItemId() == 2) {
                    listener.onRemove(miembro);
                    return true;
                }
                return false;
            });
            menuOpciones.show();
        }

        // Convierte un dataUrl en bitmap para mostrar fotos locales.
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
