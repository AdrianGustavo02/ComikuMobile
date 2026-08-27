package com.example.comiku.screens;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.comiku.R;
import com.example.comiku.data.model.BlockedUserData;
import com.example.comiku.data.repository.FriendshipRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class BlockedUsersActivity extends BaseDrawerActivity {
    private ProgressBar barraCarga;
    private TextView textoEstado;
    private LinearLayout contenedorBloqueados;
    private String uidActual = "";
    private String uidBloqueadoEnProceso = "";

    // Inicializa la pantalla de usuarios bloqueados.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            openLoginAndClearStack();
            return;
        }

        uidActual = usuario.getUid();
        setupDrawerShell(getString(R.string.bloqueados_titulo));
    }

    // Devuelve el layout de contenido de esta pantalla.
    @Override
    protected int getScreenLayoutId() {
        return R.layout.activity_blocked_users;
    }

    // Vincula vistas y carga la lista inicial.
    @Override
    protected void onScreenContentReady() {
        barraCarga = findViewById(R.id.barraCargaBloqueados);
        textoEstado = findViewById(R.id.textoEstadoBloqueados);
        contenedorBloqueados = findViewById(R.id.contenedorBloqueados);
        loadBlockedUsers();
    }

    // Recarga la lista al volver a la pantalla.
    @Override
    protected void onResume() {
        super.onResume();
        if (!TextUtils.isEmpty(uidActual)) {
            loadBlockedUsers();
        }
    }

    // Carga los usuarios bloqueados desde Firestore.
    private void loadBlockedUsers() {
        barraCarga.setVisibility(View.VISIBLE);
        textoEstado.setVisibility(View.GONE);
        contenedorBloqueados.removeAllViews();

        FriendshipRepository.getBlockedUsers(uidActual)
                .addOnSuccessListener(this::renderBlockedUsers)
                .addOnFailureListener(error -> {
                    barraCarga.setVisibility(View.GONE);
                    textoEstado.setText(getString(R.string.bloqueados_error_carga));
                    textoEstado.setVisibility(View.VISIBLE);
                })
                .addOnCompleteListener(unused -> barraCarga.setVisibility(View.GONE));
    }

    // Dibuja la lista de usuarios bloqueados.
    private void renderBlockedUsers(List<BlockedUserData> bloqueados) {
        contenedorBloqueados.removeAllViews();
        if (bloqueados == null || bloqueados.isEmpty()) {
            textoEstado.setText(getString(R.string.bloqueados_vacio));
            textoEstado.setVisibility(View.VISIBLE);
            return;
        }

        textoEstado.setVisibility(View.GONE);
        for (BlockedUserData bloqueado : bloqueados) {
            contenedorBloqueados.addView(buildBlockedUserRow(bloqueado));
        }
    }

    // Crea la fila visual de un usuario bloqueado.
    private View buildBlockedUserRow(BlockedUserData bloqueado) {
        LinearLayout tarjeta = new LinearLayout(this);
        tarjeta.setOrientation(LinearLayout.HORIZONTAL);
        tarjeta.setGravity(Gravity.CENTER_VERTICAL);
        tarjeta.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));
        LinearLayout.LayoutParams paramsTarjeta = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsTarjeta.bottomMargin = dpToPx(8);
        tarjeta.setLayoutParams(paramsTarjeta);
        tarjeta.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);

        ImageView fotoPerfil = new ImageView(this);
        LinearLayout.LayoutParams paramsFoto = new LinearLayout.LayoutParams(dpToPx(48), dpToPx(48));
        paramsFoto.setMarginEnd(dpToPx(12));
        fotoPerfil.setLayoutParams(paramsFoto);
        fotoPerfil.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap bitmapFoto = decodeDataUrl(bloqueado.fotoPerfilDataUrl);
        if (bitmapFoto != null) {
            fotoPerfil.setImageBitmap(bitmapFoto);
        } else {
            fotoPerfil.setImageResource(R.drawable.default_profile_picture);
        }
        tarjeta.addView(fotoPerfil);

        LinearLayout columna = new LinearLayout(this);
        columna.setOrientation(LinearLayout.VERTICAL);
        columna.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView textoNick = new TextView(this);
        textoNick.setText(TextUtils.isEmpty(bloqueado.nick)
                ? getString(R.string.amigos_nick_desconocido)
                : bloqueado.nick);
        textoNick.setTextSize(15f);
        textoNick.setTypeface(null, android.graphics.Typeface.BOLD);
        columna.addView(textoNick);

        TextView textoFecha = new TextView(this);
        textoFecha.setText(formatBlockedDate(bloqueado.fechaBloqueo));
        textoFecha.setTextSize(12f);
        columna.addView(textoFecha);

        tarjeta.addView(columna);

        Button botonDesbloquear = new Button(this);
        botonDesbloquear.setText(getString(R.string.bloqueados_desbloquear));
        botonDesbloquear.setOnClickListener(v -> unblockUser(bloqueado.uid));
        tarjeta.addView(botonDesbloquear);

        return tarjeta;
    }

    // Desbloquea a un usuario y refresca la lista.
    private void unblockUser(String uidBloqueado) {
        if (TextUtils.isEmpty(uidBloqueado) || TextUtils.equals(uidBloqueado, uidBloqueadoEnProceso)) {
            return;
        }

        uidBloqueadoEnProceso = uidBloqueado;
        FriendshipRepository.unblockUser(uidActual, uidBloqueado)
                .addOnSuccessListener(unused -> loadBlockedUsers())
                .addOnFailureListener(error -> textoEstado.setText(getString(R.string.bloqueados_error_carga)))
                .addOnCompleteListener(unused -> uidBloqueadoEnProceso = "");
    }

    // Formatea la fecha de bloqueo para mostrarla.
    private String formatBlockedDate(java.util.Date fechaBloqueo) {
        if (fechaBloqueo == null) {
            return "";
        }
        return String.format(
                Locale.getDefault(),
                getString(R.string.bloqueados_fecha_formato),
                new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(fechaBloqueo)
        );
    }

    // Decodifica una imagen guardada como dataUrl.
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

    // Convierte dp a pixeles.
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
