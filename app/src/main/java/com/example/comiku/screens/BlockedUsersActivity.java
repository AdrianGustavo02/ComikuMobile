package com.example.comiku.screens;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.comiku.R;
import com.example.comiku.core.ui.ThematicListUiHelper;
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
        aplicarEstiloEstadoError();
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
            aplicarEstiloEstadoVacio();
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
        tarjeta.setOrientation(LinearLayout.VERTICAL);
        tarjeta.setBackgroundResource(R.drawable.bg_carousel_container);
        tarjeta.setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14));
        LinearLayout.LayoutParams paramsTarjeta = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsTarjeta.bottomMargin = dpToPx(10);
        tarjeta.setLayoutParams(paramsTarjeta);

        LinearLayout filaIdentidad = new LinearLayout(this);
        filaIdentidad.setOrientation(LinearLayout.HORIZONTAL);
        filaIdentidad.setGravity(Gravity.CENTER_VERTICAL);
        filaIdentidad.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        RoundedImageView fotoPerfil = ThematicListUiHelper.createCircularProfileImage(this, 60);
        LinearLayout.LayoutParams paramsFoto = new LinearLayout.LayoutParams(dpToPx(60), dpToPx(60));
        paramsFoto.setMarginEnd(dpToPx(12));
        fotoPerfil.setLayoutParams(paramsFoto);
        Bitmap bitmapFoto = ThematicListUiHelper.decodeDataUrl(bloqueado.fotoPerfilDataUrl);
        if (bitmapFoto != null) {
            fotoPerfil.setImageBitmap(bitmapFoto);
        }
        filaIdentidad.addView(fotoPerfil);

        LinearLayout columna = new LinearLayout(this);
        columna.setOrientation(LinearLayout.VERTICAL);
        columna.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView textoNick = new TextView(this);
        textoNick.setText(TextUtils.isEmpty(bloqueado.nick)
                ? getString(R.string.amigos_nick_desconocido)
                : bloqueado.nick);
        textoNick.setTextSize(18f);
        textoNick.setTypeface(null, android.graphics.Typeface.BOLD);
        textoNick.setTextColor(getColor(R.color.carousel_subtitle));
        columna.addView(textoNick);

        TextView textoFecha = new TextView(this);
        textoFecha.setText(formatBlockedDate(bloqueado.fechaBloqueo));
        textoFecha.setTextSize(13f);
        textoFecha.setTextColor(getColor(R.color.carousel_subtitle));
        columna.addView(textoFecha);

        filaIdentidad.addView(columna);
        tarjeta.addView(filaIdentidad);

        LinearLayout filaAcciones = new LinearLayout(this);
        filaAcciones.setOrientation(LinearLayout.HORIZONTAL);
        filaAcciones.setGravity(Gravity.END);
        LinearLayout.LayoutParams paramsFilaAcciones = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsFilaAcciones.topMargin = dpToPx(10);
        filaAcciones.setLayoutParams(paramsFilaAcciones);

        ContextThemeWrapper contextoPrimario = new ContextThemeWrapper(this, R.style.Theme_Comiku_ButtonPrimaryAction);
        Button botonDesbloquear = new Button(contextoPrimario, null, 0);
        botonDesbloquear.setText(getString(R.string.bloqueados_desbloquear));
        botonDesbloquear.setOnClickListener(v -> unblockUser(bloqueado.uid));
        filaAcciones.addView(botonDesbloquear);

        tarjeta.addView(filaAcciones);

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

    // Convierte dp a pixeles.
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // Aplica estilo del estado vacio para centrarlo y destacarlo.
    private void aplicarEstiloEstadoVacio() {
        textoEstado.setTextColor(getColor(android.R.color.black));
        textoEstado.setTypeface(null, Typeface.BOLD);
        textoEstado.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        textoEstado.setGravity(Gravity.CENTER);
        textoEstado.setMinHeight(getResources().getDisplayMetrics().heightPixels / 2);
    }

    // Aplica estilo base para mensajes de error.
    private void aplicarEstiloEstadoError() {
        textoEstado.setTextColor(getColor(android.R.color.holo_red_dark));
        textoEstado.setTypeface(null, Typeface.NORMAL);
        textoEstado.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        textoEstado.setGravity(Gravity.START);
        textoEstado.setMinHeight(0);
    }
}
