package com.example.comiku.screens;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.comiku.R;
import com.example.comiku.core.validation.InputValidator;
import com.example.comiku.data.model.FriendRequestData;
import com.example.comiku.data.model.UserSearchData;
import com.example.comiku.data.repository.FriendshipRepository;
import com.example.comiku.data.repository.UserSearchRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class FriendsActivity extends BaseDrawerActivity {
    private EditText campoBusquedaNick;
    private ProgressBar barraCarga;
    private TextView textoEstadoBusqueda;
    private LinearLayout contenedorResultadosBusqueda;
    private TextView textoEstadoSolicitudes;
    private LinearLayout contenedorSolicitudes;
    private TextView textoEstadoAmigos;
    private LinearLayout contenedorMisAmigos;
    private final Set<String> usuariosBloqueadosPorMi = new HashSet<>();
    private final Set<String> usuariosQueMeBloquearon = new HashSet<>();
    private final Handler manejadorBusqueda = new Handler(Looper.getMainLooper());
    private Runnable tareaBusquedaPendiente;

    private String uidActual = "";
    private List<UserSearchData> amigosActuales = new ArrayList<>();
    private List<FriendRequestData> solicitudesActuales = new ArrayList<>();
    private int versionBusquedaUsuario = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            openLoginAndClearStack();
            return;
        }

        uidActual = usuario.getUid();
        setupDrawerShell(getString(R.string.amigos_titulo));
    }


    @Override
    protected int getScreenLayoutId() {
        return R.layout.activity_friends;
    }


    @Override
    protected void onScreenContentReady() {
        bindViews();
        setupSearchInput();
        loadBlockedVisibilityState();
        loadFriendData();
    }


    private void bindViews() {
        campoBusquedaNick = findViewById(R.id.campoBusquedaAmigos);
        barraCarga = findViewById(R.id.barraCargaAmigos);
        textoEstadoBusqueda = findViewById(R.id.textoEstadoBusquedaAmigos);
        contenedorResultadosBusqueda = findViewById(R.id.contenedorResultadosBusquedaAmigos);
        textoEstadoSolicitudes = findViewById(R.id.textoEstadoSolicitudesAmigos);
        contenedorSolicitudes = findViewById(R.id.contenedorSolicitudesAmigos);
        textoEstadoAmigos = findViewById(R.id.textoEstadoMisAmigos);
        contenedorMisAmigos = findViewById(R.id.contenedorMisAmigos);
    }

    // Conecta la busqueda por nick con limpieza de caracteres.
    private void setupSearchInput() {
        campoBusquedaNick.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String textoLimpio = InputValidator.sanitizeForbiddenChars(String.valueOf(s));
                if (!TextUtils.equals(textoLimpio, String.valueOf(s))) {
                    campoBusquedaNick.removeTextChangedListener(this);
                    campoBusquedaNick.setText(textoLimpio);
                    campoBusquedaNick.setSelection(textoLimpio.length());
                    campoBusquedaNick.addTextChangedListener(this);
                }
                scheduleUserSearch(textoLimpio);
            }
        });
    }

    // Programa la busqueda para evitar resultados repetidos al escribir rapido.
    private void scheduleUserSearch(String termino) {
        if (tareaBusquedaPendiente != null) {
            manejadorBusqueda.removeCallbacks(tareaBusquedaPendiente);
        }

        final String terminoProgramado = termino;
        final int versionProgramada = ++versionBusquedaUsuario;
        tareaBusquedaPendiente = () -> performUserSearch(terminoProgramado, versionProgramada);
        manejadorBusqueda.postDelayed(tareaBusquedaPendiente, 300L);
    }

    // Carga solicitudes pendientes y amigos
    private void loadFriendData() {
        barraCarga.setVisibility(View.VISIBLE);
        textoEstadoSolicitudes.setVisibility(View.GONE);
        contenedorSolicitudes.removeAllViews();
        textoEstadoAmigos.setVisibility(View.GONE);
        contenedorMisAmigos.removeAllViews();

        FriendshipRepository.getFriendRequests(uidActual)
                .addOnSuccessListener(solicitudes -> {
                    solicitudesActuales = solicitudes != null ? solicitudes : new ArrayList<>();
                    renderSolicitudes();
                    loadFriendsOnly();
                })
                .addOnFailureListener(error -> {
                    barraCarga.setVisibility(View.GONE);
                    textoEstadoSolicitudes.setText(getString(R.string.amigos_solicitudes_error));
                    textoEstadoSolicitudes.setVisibility(View.VISIBLE);
                    textoEstadoAmigos.setText(getString(R.string.amigos_error_carga));
                    textoEstadoAmigos.setVisibility(View.VISIBLE);
                });
    }

    // Carga los usuarios bloqueados para deshabilitar accesos en busqueda.
    private void loadBlockedVisibilityState() {
        if (TextUtils.isEmpty(uidActual)) {
            usuariosBloqueadosPorMi.clear();
            usuariosQueMeBloquearon.clear();
            return;
        }

        FriendshipRepository.getBlockedUserIds(uidActual)
                .addOnSuccessListener(bloqueados -> {
                    usuariosBloqueadosPorMi.clear();
                    if (bloqueados != null) {
                        usuariosBloqueadosPorMi.addAll(bloqueados);
                    }
                });

        FriendshipRepository.getUsersWhoBlockedUserIds(uidActual)
                .addOnSuccessListener(bloqueadores -> {
                    usuariosQueMeBloquearon.clear();
                    if (bloqueadores != null) {
                        usuariosQueMeBloquearon.addAll(bloqueadores);
                    }
                });
    }


    private void loadFriendsOnly() {
        UserSearchRepository.getUserFriends(uidActual)
                .addOnSuccessListener(amigos -> {
                    barraCarga.setVisibility(View.GONE);
                    amigosActuales = amigos != null ? amigos : new ArrayList<>();
                    renderFriends();
                })
                .addOnFailureListener(error -> {
                    barraCarga.setVisibility(View.GONE);
                    textoEstadoAmigos.setText(getString(R.string.amigos_error_carga));
                    textoEstadoAmigos.setVisibility(View.VISIBLE);
                });
    }

    // Busca usuarios por nick y muestra coincidencias.
    private void performUserSearch(String termino, int versionSolicitud) {
        if (versionSolicitud != versionBusquedaUsuario) {
            return;
        }
        contenedorResultadosBusqueda.removeAllViews();
        textoEstadoBusqueda.setVisibility(View.GONE);

        if (TextUtils.isEmpty(termino)) {
            barraCarga.setVisibility(View.GONE);
            textoEstadoBusqueda.setText(getString(R.string.amigos_busqueda_vacia));
            textoEstadoBusqueda.setVisibility(View.VISIBLE);
            return;
        }

        barraCarga.setVisibility(View.VISIBLE);
        UserSearchRepository.searchUsersByNick(uidActual, termino)
                .addOnSuccessListener(resultados -> {
                    if (versionSolicitud != versionBusquedaUsuario) {
                        return;
                    }
                    barraCarga.setVisibility(View.GONE);
                    if (resultados == null || resultados.isEmpty()) {
                        textoEstadoBusqueda.setText(getString(R.string.amigos_busqueda_sin_resultados));
                        textoEstadoBusqueda.setVisibility(View.VISIBLE);
                        return;
                    }
                    renderSearchResults(resultados);
                })
                .addOnFailureListener(error -> {
                    if (versionSolicitud != versionBusquedaUsuario) {
                        return;
                    }
                    barraCarga.setVisibility(View.GONE);
                    textoEstadoBusqueda.setText(getString(R.string.amigos_busqueda_error));
                    textoEstadoBusqueda.setVisibility(View.VISIBLE);
                });
    }

    // Muestra resultados de busqueda y abre perfil al tocar.
    private void renderSearchResults(List<UserSearchData> usuarios) {
        for (UserSearchData usuario : usuarios) {
            boolean bloqueadoPorMi = usuariosBloqueadosPorMi.contains(usuario.uid);
            boolean meBloquearon = usuariosQueMeBloquearon.contains(usuario.uid);
            boolean puedeAbrir = !bloqueadoPorMi && !meBloquearon;
            String tipoBloqueo = null;
            if (bloqueadoPorMi) {
                tipoBloqueo = "bloqueado_por_mi";
            } else if (meBloquearon) {
                tipoBloqueo = "me_bloqueo";
            }
            contenedorResultadosBusqueda.addView(buildUserInfoRow(
                    usuario.uid,
                    usuario.nick,
                    usuario.nombre,
                    usuario.fotoPerfilDataUrl,
                    true,
                    puedeAbrir,
                    tipoBloqueo
            ));
        }
    }

    // Muestra solicitudes pendientes con acciones aceptar y rechazar
    private void renderSolicitudes() {
        contenedorSolicitudes.removeAllViews();
        if (solicitudesActuales.isEmpty()) {
            textoEstadoSolicitudes.setText(getString(R.string.amigos_solicitudes_vacia));
            textoEstadoSolicitudes.setVisibility(View.VISIBLE);
            return;
        }

        textoEstadoSolicitudes.setVisibility(View.GONE);
        for (FriendRequestData solicitud : solicitudesActuales) {
            contenedorSolicitudes.addView(buildSolicitudRow(solicitud));
        }
    }

    // Muestra la lista de amigos en la seccion principal
    private void renderFriends() {
        contenedorMisAmigos.removeAllViews();
        if (amigosActuales.isEmpty()) {
            textoEstadoAmigos.setText(getString(R.string.amigos_sin_amigos));
            textoEstadoAmigos.setVisibility(View.VISIBLE);
            return;
        }

        textoEstadoAmigos.setVisibility(View.GONE);
        for (UserSearchData usuario : amigosActuales) {
            contenedorMisAmigos.addView(buildUserInfoRow(
                    usuario.uid,
                    usuario.nick,
                    "",
                    usuario.fotoPerfilDataUrl,
                    false,
                    true
            ));
        }
    }

    // Crea una fila con foto, nick y nombre opcional.
    private View buildUserInfoRow(
            String uidPerfil,
            String nick,
            String nombre,
            String fotoPerfilDataUrl,
            boolean mostrarNombre,
            boolean puedeAbrirPerfil
    ) {
        return buildUserInfoRow(
                uidPerfil,
                nick,
                nombre,
                fotoPerfilDataUrl,
                mostrarNombre,
                puedeAbrirPerfil,
                null
        );
    }

    // Crea una fila con foto, nick y nombre opcional.
    private View buildUserInfoRow(
            String uidPerfil,
            String nick,
            String nombre,
            String fotoPerfilDataUrl,
            boolean mostrarNombre,
            boolean puedeAbrirPerfil,
            String tipoBloqueo
    ) {
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

        ImageView imagenPerfil = new ImageView(this);
        LinearLayout.LayoutParams paramsImagen = new LinearLayout.LayoutParams(dpToPx(48), dpToPx(48));
        paramsImagen.setMarginEnd(dpToPx(12));
        imagenPerfil.setLayoutParams(paramsImagen);
        imagenPerfil.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap bitmapFoto = decodeDataUrl(fotoPerfilDataUrl);
        if (bitmapFoto != null) {
            imagenPerfil.setImageBitmap(bitmapFoto);
        } else {
            imagenPerfil.setImageResource(R.drawable.default_profile_picture);
        }
        imagenPerfil.setClickable(puedeAbrirPerfil);
        if (puedeAbrirPerfil) {
            imagenPerfil.setOnClickListener(v -> openUserProfile(uidPerfil));
        }
        tarjeta.addView(imagenPerfil);

        LinearLayout columnaTextos = new LinearLayout(this);
        columnaTextos.setOrientation(LinearLayout.VERTICAL);
        columnaTextos.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView textoNick = new TextView(this);
        textoNick.setTextSize(15f);
        textoNick.setTypeface(null, android.graphics.Typeface.BOLD);
        textoNick.setText(TextUtils.isEmpty(nick)
                ? getString(R.string.amigos_nick_desconocido)
                : nick);
        textoNick.setTextColor(getResources().getColor(android.R.color.holo_blue_dark, getTheme()));
        textoNick.setClickable(puedeAbrirPerfil);
        if (puedeAbrirPerfil) {
            textoNick.setOnClickListener(v -> openUserProfile(uidPerfil));
        } else {
            textoNick.setAlpha(0.6f);
        }
        columnaTextos.addView(textoNick);

        if (mostrarNombre && !TextUtils.isEmpty(nombre)) {
            TextView textoNombre = new TextView(this);
            textoNombre.setTextSize(13f);
            textoNombre.setText(nombre);
            columnaTextos.addView(textoNombre);
        }

        if (!puedeAbrirPerfil && tipoBloqueo != null) {
            TextView textoNoDisponible = new TextView(this);
            if ("bloqueado_por_mi".equals(tipoBloqueo)) {
                textoNoDisponible.setText(getString(R.string.perfil_bloqueado_por_mi));
            } else if ("me_bloqueo".equals(tipoBloqueo)) {
                textoNoDisponible.setText(getString(R.string.perfil_me_bloqueo));
            } else {
                textoNoDisponible.setText(getString(R.string.perfil_no_disponible));
            }
            textoNoDisponible.setTextSize(12f);
            columnaTextos.addView(textoNoDisponible);
        }

        tarjeta.addView(columnaTextos);
        return tarjeta;
    }

    // Crea una fila de solicitud con botones aceptar y rechazar.
    private View buildSolicitudRow(FriendRequestData solicitud) {
        LinearLayout contenedor = new LinearLayout(this);
        contenedor.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams paramsContenedor = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsContenedor.bottomMargin = dpToPx(8);
        contenedor.setLayoutParams(paramsContenedor);
        contenedor.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));
        contenedor.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);

        contenedor.addView(buildUserInfoRow(
                solicitud.senderUid,
                solicitud.nick,
                "",
                solicitud.fotoPerfilDataUrl,
                false,
                !usuariosBloqueadosPorMi.contains(solicitud.senderUid)
                        && !usuariosQueMeBloquearon.contains(solicitud.senderUid)
        ));

        LinearLayout filaAcciones = new LinearLayout(this);
        filaAcciones.setOrientation(LinearLayout.HORIZONTAL);
        filaAcciones.setGravity(Gravity.END);

        Button botonRechazar = new Button(this);
        botonRechazar.setText(getString(R.string.amigos_solicitud_rechazar));
        botonRechazar.setOnClickListener(v -> handleDeclineRequest(solicitud.senderUid));
        filaAcciones.addView(botonRechazar);

        Button botonAceptar = new Button(this);
        botonAceptar.setText(getString(R.string.amigos_solicitud_aceptar));
        LinearLayout.LayoutParams paramsAceptar = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsAceptar.setMarginStart(dpToPx(8));
        botonAceptar.setLayoutParams(paramsAceptar);
        botonAceptar.setOnClickListener(v -> handleAcceptRequest(solicitud.senderUid));
        filaAcciones.addView(botonAceptar);

        contenedor.addView(filaAcciones);
        return contenedor;
    }

    // Acepta una solicitud y recarga estado.
    private void handleAcceptRequest(String uidEmisor) {
        barraCarga.setVisibility(View.VISIBLE);
        FriendshipRepository.acceptFriendRequest(uidActual, uidEmisor)
                .addOnSuccessListener(unused -> loadFriendData())
                .addOnFailureListener(error -> {
                    barraCarga.setVisibility(View.GONE);
                    textoEstadoSolicitudes.setText(getString(R.string.amigos_solicitudes_error));
                    textoEstadoSolicitudes.setVisibility(View.VISIBLE);
                });
    }

    // Rechaza una solicitud y recarga estado.
    private void handleDeclineRequest(String uidEmisor) {
        barraCarga.setVisibility(View.VISIBLE);
        FriendshipRepository.declineFriendRequest(uidActual, uidEmisor)
                .addOnSuccessListener(unused -> loadFriendData())
                .addOnFailureListener(error -> {
                    barraCarga.setVisibility(View.GONE);
                    textoEstadoSolicitudes.setText(getString(R.string.amigos_solicitudes_error));
                    textoEstadoSolicitudes.setVisibility(View.VISIBLE);
                });
    }

    // Refresca datos al volver desde perfil externo.
    @Override
    protected void onResume() {
        super.onResume();
        if (!TextUtils.isEmpty(uidActual)) {
            loadBlockedVisibilityState();
            loadFriendData();
        }
    }


    @Override
    protected void onDestroy() {
        manejadorBusqueda.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // Navega al perfil del usuario seleccionado.
    private void openUserProfile(String uidPerfil) {
        if (TextUtils.isEmpty(uidPerfil)) {
            return;
        }

        FriendshipRepository.canOpenUserProfile(uidActual, uidPerfil)
                .addOnSuccessListener(canOpen -> {
                    if (!Boolean.TRUE.equals(canOpen)) {
                        Toast.makeText(this, R.string.perfil_acceso_bloqueado, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Intent pantallaPerfil = new Intent(this, ProfileActivity.class);
                    pantallaPerfil.putExtra(ProfileActivity.EXTRA_USER_ID, uidPerfil);
                    startActivity(pantallaPerfil);
                })
                .addOnFailureListener(error ->
                        Toast.makeText(this, R.string.error_perfil_carga, Toast.LENGTH_SHORT).show());
    }

    // Convierte dataUrl a bitmap para foto de perfil.
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
