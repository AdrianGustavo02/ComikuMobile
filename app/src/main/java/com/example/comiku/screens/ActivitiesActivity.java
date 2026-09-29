package com.example.comiku.screens;

import android.content.Intent;
import android.view.ContextThemeWrapper;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.example.comiku.R;
import com.example.comiku.core.ui.ThematicListUiHelper;
import com.example.comiku.core.ui.ToastUtils;
import com.example.comiku.core.validation.InputValidator;
import com.example.comiku.data.model.FriendActivityCommentData;
import com.example.comiku.data.model.FriendActivityCommentPageData;
import com.example.comiku.data.model.FriendActivityData;
import com.example.comiku.data.model.FriendActivityListData;
import com.example.comiku.data.model.FriendActivityPageData;
import com.example.comiku.data.model.FriendActivityType;
import com.example.comiku.data.model.FriendActivityVolumeData;
import com.example.comiku.data.model.UserSearchData;
import com.example.comiku.data.repository.FriendActivityRepository;
import com.example.comiku.data.repository.FriendshipRepository;
import com.example.comiku.data.repository.UserSearchRepository;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

public class ActivitiesActivity extends BaseDrawerActivity {
    public static final String EXTRA_ACTIVITY_ID = "extra_activity_id";

    private static final int TAMANO_PAGINA_ACTIVIDADES = 10;
    private static final int TAMANO_PAGINA_COMENTARIOS = 10;
    private static final TimeZone ZONA_HORARIA_ACTIVIDADES = TimeZone.getTimeZone("America/Argentina/Buenos_Aires");

    private ProgressBar barraCarga;
    private TextView textoEstado;
    private LinearLayout contenedorActividades;
    private Button botonCargarMas;

    private String uidActual = "";
    private boolean cargandoActividades;
    private final List<FriendActivityData> actividades = new ArrayList<>();
    private String cursorActividadId;
    private Date cursorActividadFecha;
    private boolean hayMasActividades;
    private String actividadPendienteId = "";
    private boolean actividadPendienteAbierta;

    // Inicializa la pantalla de actividades sociales.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            openLoginAndClearStack();
            return;
        }

        uidActual = usuario.getUid();
        actividadPendienteId = getIntent().getStringExtra(EXTRA_ACTIVITY_ID);
        actividadPendienteAbierta = false;
        setupDrawerShell(getString(R.string.actividades_titulo));
    }

    // Devuelve el layout de contenido de la pantalla.
    @Override
    protected int getScreenLayoutId() {
        return R.layout.activity_activities;
    }

    // Prepara vistas y acciones iniciales de la pantalla.
    @Override
    protected void onScreenContentReady() {
        bindViews();
        botonCargarMas.setOnClickListener(v -> loadActivities(false));
        loadActivities(true);
    }

    // Recarga la primera pagina al volver a la pantalla.
    @Override
    protected void onResume() {
        super.onResume();
        if (!TextUtils.isEmpty(uidActual)) {
            loadActivities(true);
        }
    }

    // Vincula las vistas del layout principal.
    private void bindViews() {
        barraCarga = findViewById(R.id.barraCargaActividades);
        textoEstado = findViewById(R.id.textoEstadoActividades);
        contenedorActividades = findViewById(R.id.contenedorActividades);
        botonCargarMas = findViewById(R.id.botonCargarMasActividades);
    }

    // Carga una pagina de actividades con filtros de amistad y bloqueo.
    private void loadActivities(boolean reiniciar) {
        if (cargandoActividades) {
            return;
        }

        if (reiniciar) {
            actividades.clear();
            cursorActividadId = null;
            cursorActividadFecha = null;
            hayMasActividades = false;
            contenedorActividades.removeAllViews();
            botonCargarMas.setVisibility(View.GONE);
        }

        cargandoActividades = true;
        barraCarga.setVisibility(View.VISIBLE);
        textoEstado.setVisibility(View.VISIBLE);
        textoEstado.setText(getString(R.string.actividades_cargando));
        botonCargarMas.setEnabled(false);

        resolveAllowedFriendIds()
                .continueWithTask(taskPermitidos -> {
                    if (!taskPermitidos.isSuccessful() || taskPermitidos.getResult() == null) {
                        throw getTaskError(taskPermitidos, getString(R.string.actividades_error_carga));
                    }
                    return FriendActivityRepository.getActivitiesPage(
                            taskPermitidos.getResult(),
                            TAMANO_PAGINA_ACTIVIDADES,
                            uidActual,
                            reiniciar ? null : cursorActividadId,
                            reiniciar ? null : cursorActividadFecha
                    );
                })
                .addOnSuccessListener(this::applyActivityPage)
                .addOnFailureListener(error -> showListError(
                        error.getMessage() != null
                                ? error.getMessage()
                                : getString(R.string.actividades_error_carga)
                ))
                .addOnCompleteListener(unused -> {
                    cargandoActividades = false;
                    barraCarga.setVisibility(View.GONE);
                    botonCargarMas.setEnabled(true);
                });
    }

    // Obtiene amigos visibles luego de filtrar bloqueados en ambos sentidos.
    private Task<List<String>> resolveAllowedFriendIds() {
        Task<List<UserSearchData>> tareaAmigos = UserSearchRepository.getUserFriends(uidActual);
        Task<List<String>> tareaBloqueados = FriendshipRepository.getBlockedUserIds(uidActual);
        Task<List<String>> tareaBloqueadores = FriendshipRepository.getUsersWhoBlockedUserIds(uidActual);

        return Tasks.whenAllSuccess(tareaAmigos, tareaBloqueados, tareaBloqueadores)
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null || task.getResult().size() < 3) {
                        throw getTaskError(task, getString(R.string.actividades_error_carga));
                    }

                    Object objAmigos = task.getResult().get(0);
                    Object objBloqueados = task.getResult().get(1);
                    Object objBloqueadores = task.getResult().get(2);

                    List<UserSearchData> amigos = objAmigos instanceof List
                            ? (List<UserSearchData>) objAmigos
                            : new ArrayList<>();
                    List<String> bloqueados = objBloqueados instanceof List
                            ? (List<String>) objBloqueados
                            : new ArrayList<>();
                    List<String> bloqueadores = objBloqueadores instanceof List
                            ? (List<String>) objBloqueadores
                            : new ArrayList<>();

                    Set<String> setBloqueos = new HashSet<>();
                    setBloqueos.addAll(bloqueados);
                    setBloqueos.addAll(bloqueadores);

                    List<String> permitidos = new ArrayList<>();
                    for (UserSearchData amigo : amigos) {
                        if (amigo == null || TextUtils.isEmpty(amigo.uid)) {
                            continue;
                        }
                        if (!setBloqueos.contains(amigo.uid)) {
                            permitidos.add(amigo.uid);
                        }
                    }
                    return permitidos;
                });
    }

    // Aplica la pagina recibida y refresca el listado en pantalla.
    private void applyActivityPage(FriendActivityPageData pagina) {
        if (pagina == null) {
            showListError(getString(R.string.actividades_error_carga));
            return;
        }

        if (pagina.actividades != null) {
            Set<String> idsExistentes = new HashSet<>();
            for (FriendActivityData actividad : actividades) {
                idsExistentes.add(actividad.id);
            }
            for (FriendActivityData actividadNueva : pagina.actividades) {
                if (actividadNueva == null || idsExistentes.contains(actividadNueva.id)) {
                    continue;
                }
                actividades.add(actividadNueva);
                idsExistentes.add(actividadNueva.id);
            }
        }

        cursorActividadId = pagina.cursorId;
        cursorActividadFecha = pagina.cursorFecha;
        hayMasActividades = pagina.hayMas;
        renderActivitiesList();
        openPendingActivityIfNeeded();
    }

    // Dibuja las actividades y el estado de lista vacia o paginacion.
    private void renderActivitiesList() {
        contenedorActividades.removeAllViews();
        if (actividades.isEmpty()) {
            textoEstado.setText(getString(R.string.actividades_vacia));
            textoEstado.setVisibility(View.VISIBLE);
            botonCargarMas.setVisibility(View.GONE);
            return;
        }

        textoEstado.setVisibility(View.GONE);
        for (FriendActivityData actividad : actividades) {
            contenedorActividades.addView(buildActivityCard(actividad));
        }

        botonCargarMas.setVisibility(hayMasActividades ? View.VISIBLE : View.GONE);
    }

    // Muestra el error principal del feed de actividades.
    private void showListError(String mensaje) {
        textoEstado.setText(mensaje);
        textoEstado.setVisibility(View.VISIBLE);
        if (actividades.isEmpty()) {
            botonCargarMas.setVisibility(View.GONE);
        }
    }

    // Crea la tarjeta visible de una actividad del feed.
    private View buildActivityCard(FriendActivityData actividad) {
        LinearLayout tarjeta = new LinearLayout(this);
        tarjeta.setOrientation(LinearLayout.VERTICAL);
        tarjeta.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));
        LinearLayout.LayoutParams paramsTarjeta = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsTarjeta.bottomMargin = dpToPx(10);
        tarjeta.setLayoutParams(paramsTarjeta);
        tarjeta.setBackgroundResource(R.drawable.bg_activity_card);

        TextView textoDescripcion = new TextView(this);
        textoDescripcion.setTypeface(null, android.graphics.Typeface.BOLD);
        textoDescripcion.setText(buildActivityDescription(actividad));
        textoDescripcion.setTextSize(16f);
        textoDescripcion.setTextColor(getColor(R.color.carousel_subtitle));
        tarjeta.addView(textoDescripcion);

        TextView textoFecha = new TextView(this);
        textoFecha.setText(formatDateTime(actividad.fecha));
        textoFecha.setTextSize(13f);
        textoFecha.setTextColor(getColor(R.color.carousel_subtitle));
        LinearLayout.LayoutParams paramsFecha = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsFecha.topMargin = dpToPx(4);
        textoFecha.setLayoutParams(paramsFecha);
        tarjeta.addView(textoFecha);

        if (actividad.tipoActividad == FriendActivityType.THEMATIC_LIST_CREATE) {
            tarjeta.addView(buildListPreview(actividad.listas));
        } else {
            tarjeta.addView(buildVolumePreview(actividad.tomos, 3));
        }

        LinearLayout pie = new LinearLayout(this);
        pie.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams paramsPie = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsPie.topMargin = dpToPx(8);
        pie.setLayoutParams(paramsPie);

        pie.addView(buildStatItem(R.drawable.ic_like_outline, actividad.cantidadLikes, false));
        pie.addView(buildStatItem(R.drawable.ic_comment_outline, actividad.cantidadComentarios, true));

        tarjeta.addView(pie);
        tarjeta.setOnClickListener(v -> openActivityDialog(actividad));
        return tarjeta;
    }

    // Crea un indicador con icono y cantidad para likes o comentarios.
    private View buildStatItem(int iconoResId, int cantidad, boolean agregarMargenInicio) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams paramsItem = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        if (agregarMargenInicio) {
            paramsItem.leftMargin = dpToPx(12);
        }
        item.setLayoutParams(paramsItem);

        ImageView icono = new ImageView(this);
        LinearLayout.LayoutParams paramsIcono = new LinearLayout.LayoutParams(dpToPx(18), dpToPx(18));
        paramsIcono.rightMargin = dpToPx(6);
        icono.setLayoutParams(paramsIcono);
        icono.setImageResource(iconoResId);
        icono.setColorFilter(Color.WHITE);
        item.addView(icono);

        TextView textoCantidad = new TextView(this);
        textoCantidad.setText(String.valueOf(cantidad));
        textoCantidad.setTextColor(Color.WHITE);
        textoCantidad.setTextSize(15f);
        item.addView(textoCantidad);

        return item;
    }

    // Crea el texto legible de una actividad para lista y modal.
    private String buildActivityDescription(FriendActivityData actividad) {
        String nick = !TextUtils.isEmpty(actividad.actorNick)
                ? actividad.actorNick
                : getString(R.string.amigos_nick_desconocido);
        int cantidad = actividad.cantidadElementos > 0
                ? actividad.cantidadElementos
                : Math.max(actividad.tomos.size(), actividad.listas.size());
        if (actividad.tipoActividad == FriendActivityType.LIBRARY_ADD) {
            return getString(R.string.actividades_texto_biblioteca, nick, cantidad);
        }
        if (actividad.tipoActividad == FriendActivityType.WISHLIST_ADD) {
            return getString(R.string.actividades_texto_deseados, nick, cantidad);
        }
        if (actividad.tipoActividad == FriendActivityType.THEMATIC_LIST_CREATE) {
            return getString(R.string.actividades_texto_lista, nick, cantidad);
        }
        return getString(R.string.actividades_texto_generico, nick);
    }

    // Crea una vista compacta con nombres de listas tematicas.
    private View buildListPreview(List<FriendActivityListData> listas) {
        LinearLayout contenedor = new LinearLayout(this);
        contenedor.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dpToPx(8);
        contenedor.setLayoutParams(params);

        if (listas == null || listas.isEmpty()) {
            return contenedor;
        }

        int limite = Math.min(5, listas.size());
        for (int i = 0; i < limite; i++) {
            TextView nombreLista = new TextView(this);
            nombreLista.setText("• " + safeText(listas.get(i).nombreLista));
            contenedor.addView(nombreLista);
        }
        return contenedor;
    }

    // Crea una vista compacta con portadas de tomos.
    private View buildVolumePreview(List<FriendActivityVolumeData> tomos, int limite) {
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dpToPx(8);
        fila.setLayoutParams(params);

        if (tomos == null || tomos.isEmpty()) {
            return fila;
        }

        int cantidad = Math.min(Math.max(1, limite), tomos.size());
        for (int i = 0; i < cantidad; i++) {
            ImageView portada = createCoverImage(tomos.get(i).portadaDataUrl, 56, 84);
            LinearLayout.LayoutParams paramsPortada = (LinearLayout.LayoutParams) portada.getLayoutParams();
            if (i > 0) {
                paramsPortada.leftMargin = dpToPx(6);
                portada.setLayoutParams(paramsPortada);
            }
            fila.addView(portada);
        }
        return fila;
    }

    // Abre el modal de detalle de una actividad.
    private void openActivityDialog(FriendActivityData actividad) {
        View vistaDialogo = LayoutInflater.from(this).inflate(R.layout.dialog_activity_detail, null, false);
        AlertDialog dialogo = new AlertDialog.Builder(this)
                .setView(vistaDialogo)
                .create();

        DialogState estadoDialogo = new DialogState();
        estadoDialogo.actividad = actividad;
        estadoDialogo.dialogo = dialogo;
        estadoDialogo.textoTitulo = vistaDialogo.findViewById(R.id.textoTituloActividadDialog);
        estadoDialogo.textoFecha = vistaDialogo.findViewById(R.id.textoFechaActividadDialog);
        estadoDialogo.botonLike = vistaDialogo.findViewById(R.id.botonLikeActividadDialog);
        estadoDialogo.textoLikes = vistaDialogo.findViewById(R.id.textoLikesActividadDialog);
        estadoDialogo.textoComentarios = vistaDialogo.findViewById(R.id.textoComentariosActividadDialog);
        estadoDialogo.textoTituloComentarios = vistaDialogo.findViewById(R.id.textoTituloComentariosActividadDialog);
        estadoDialogo.contenedorDetalle = vistaDialogo.findViewById(R.id.contenedorDetalleActividadDialog);
        estadoDialogo.campoComentario = vistaDialogo.findViewById(R.id.campoComentarioActividadDialog);
        estadoDialogo.botonEnviarComentario = vistaDialogo.findViewById(R.id.botonEnviarComentarioActividadDialog);
        estadoDialogo.textoEstadoComentarios = vistaDialogo.findViewById(R.id.textoEstadoComentariosActividadDialog);
        estadoDialogo.contenedorComentarios = vistaDialogo.findViewById(R.id.contenedorComentariosActividadDialog);
        estadoDialogo.botonCargarMasComentarios = vistaDialogo.findViewById(R.id.botonCargarMasComentariosActividadDialog);
        estadoDialogo.cantidadLikes = actividad.cantidadLikes;
        estadoDialogo.cantidadComentarios = actividad.cantidadComentarios;
        estadoDialogo.botonLike.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_like_outline, 0, 0, 0);
        estadoDialogo.botonLike.setCompoundDrawablePadding(dpToPx(6));

        estadoDialogo.textoTitulo.setText(buildActivityDescription(actividad));
        estadoDialogo.textoTitulo.setTextColor(Color.WHITE);
        estadoDialogo.textoFecha.setText(formatDateTime(actividad.fecha));
        estadoDialogo.textoFecha.setTextColor(Color.WHITE);
        estadoDialogo.textoTituloComentarios.setTextColor(Color.WHITE);
        renderDialogStats(estadoDialogo);
        renderDialogDetail(estadoDialogo);
        setupDialogActions(estadoDialogo);

        dialogo.show();
        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setBackgroundDrawableResource(R.drawable.bg_activity_dialog_rounded);
        }
        loadLikeStatusForDialog(estadoDialogo);
        loadCommentsForDialog(estadoDialogo, true);
    }

    // Renderiza el contenido central segun tipo de actividad.
    private void renderDialogDetail(DialogState estadoDialogo) {
        estadoDialogo.contenedorDetalle.removeAllViews();
        if (estadoDialogo.actividad.tipoActividad == FriendActivityType.THEMATIC_LIST_CREATE) {
            if (estadoDialogo.actividad.listas.isEmpty()) {
                return;
            }
            for (FriendActivityListData lista : estadoDialogo.actividad.listas) {
                Button botonLista = new Button(this);
                botonLista.setText(safeText(lista.nombreLista));
                botonLista.setOnClickListener(v -> openThematicListDetail(lista.listaId));
                estadoDialogo.contenedorDetalle.addView(botonLista);
            }
            return;
        }

        LinearLayout filaPortadas = new LinearLayout(this);
        filaPortadas.setOrientation(LinearLayout.HORIZONTAL);
        filaPortadas.setGravity(Gravity.START);
        for (FriendActivityVolumeData tomo : estadoDialogo.actividad.tomos) {
            ImageView portada = createCoverImage(tomo.portadaDataUrl, 72, 108);
            portada.setOnClickListener(v -> openVolumeDetail(tomo.comicId, tomo.tomoId));
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) portada.getLayoutParams();
            params.rightMargin = dpToPx(8);
            portada.setLayoutParams(params);
            filaPortadas.addView(portada);
        }
        estadoDialogo.contenedorDetalle.addView(filaPortadas);
    }

    // Conecta acciones de like y comentarios del modal.
    private void setupDialogActions(DialogState estadoDialogo) {
        estadoDialogo.botonLike.setOnClickListener(v -> toggleLikeInDialog(estadoDialogo));
        estadoDialogo.botonEnviarComentario.setOnClickListener(v -> addCommentInDialog(estadoDialogo));
        estadoDialogo.botonCargarMasComentarios.setOnClickListener(v -> loadCommentsForDialog(estadoDialogo, false));

        estadoDialogo.campoComentario.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String limpio = InputValidator.sanitizeForbiddenChars(String.valueOf(s));
                if (!TextUtils.equals(limpio, String.valueOf(s))) {
                    estadoDialogo.campoComentario.removeTextChangedListener(this);
                    estadoDialogo.campoComentario.setText(limpio);
                    estadoDialogo.campoComentario.setSelection(limpio.length());
                    estadoDialogo.campoComentario.addTextChangedListener(this);
                }
            }
        });
    }

    // Carga estado de like y cantidad real para el modal.
    private void loadLikeStatusForDialog(DialogState estadoDialogo) {
        FriendActivityRepository.getUserLikeStatus(estadoDialogo.actividad.id, uidActual)
                .addOnSuccessListener(liked -> {
                    estadoDialogo.likeActivo = liked;
                    renderDialogStats(estadoDialogo);
                });
        FriendActivityRepository.getLikeCount(estadoDialogo.actividad.id)
                .addOnSuccessListener(cantidad -> {
                    estadoDialogo.cantidadLikes = Math.max(0, cantidad);
                    renderDialogStats(estadoDialogo);
                    updateActivityStats(estadoDialogo.actividad.id, estadoDialogo.cantidadLikes, null);
                });
    }

    // Alterna like desde el modal y actualiza contadores.
    private void toggleLikeInDialog(DialogState estadoDialogo) {
        if (estadoDialogo.procesandoLike) {
            return;
        }
        estadoDialogo.procesandoLike = true;
        estadoDialogo.botonLike.setEnabled(false);

        FriendActivityRepository.toggleLikeActivity(estadoDialogo.actividad.id, uidActual)
                .addOnSuccessListener(liked -> {
                    estadoDialogo.likeActivo = liked;
                    if (liked) {
                        estadoDialogo.cantidadLikes++;
                    } else {
                        estadoDialogo.cantidadLikes = Math.max(0, estadoDialogo.cantidadLikes - 1);
                    }
                    renderDialogStats(estadoDialogo);
                    updateActivityStats(estadoDialogo.actividad.id, estadoDialogo.cantidadLikes, null);
                })
                .addOnFailureListener(error ->
                        ToastUtils.showTextToast(this, R.string.actividades_error_like, Toast.LENGTH_SHORT)
                )
                .addOnCompleteListener(unused -> {
                    estadoDialogo.procesandoLike = false;
                    estadoDialogo.botonLike.setEnabled(true);
                });
    }

    // Agrega un comentario nuevo desde el modal.
    private void addCommentInDialog(DialogState estadoDialogo) {
        if (estadoDialogo.procesandoComentario) {
            return;
        }

        String texto = InputValidator.sanitizeForbiddenChars(
                String.valueOf(estadoDialogo.campoComentario.getText())
        ).trim();
        if (TextUtils.isEmpty(texto)) {
            estadoDialogo.textoEstadoComentarios.setText(getString(R.string.actividades_error_comentario_vacio));
            estadoDialogo.textoEstadoComentarios.setVisibility(View.VISIBLE);
            return;
        }

        estadoDialogo.procesandoComentario = true;
        estadoDialogo.botonEnviarComentario.setEnabled(false);
        estadoDialogo.textoEstadoComentarios.setVisibility(View.GONE);

        FriendActivityRepository.addComment(estadoDialogo.actividad.id, uidActual, texto)
                .addOnSuccessListener(unused -> {
                    estadoDialogo.campoComentario.setText("");
                    estadoDialogo.cantidadComentarios++;
                    renderDialogStats(estadoDialogo);
                    updateActivityStats(estadoDialogo.actividad.id, null, estadoDialogo.cantidadComentarios);
                    loadCommentsForDialog(estadoDialogo, true);
                })
                .addOnFailureListener(error -> {
                    String mensaje = error.getMessage() != null
                            ? error.getMessage()
                            : getString(R.string.actividades_error_comentario);
                    estadoDialogo.textoEstadoComentarios.setText(mensaje);
                    estadoDialogo.textoEstadoComentarios.setVisibility(View.VISIBLE);
                })
                .addOnCompleteListener(unused -> {
                    estadoDialogo.procesandoComentario = false;
                    estadoDialogo.botonEnviarComentario.setEnabled(true);
                });
    }

    // Carga comentarios del modal con paginacion.
    private void loadCommentsForDialog(DialogState estadoDialogo, boolean reiniciar) {
        if (estadoDialogo.cargandoComentarios) {
            return;
        }

        if (reiniciar) {
            estadoDialogo.cursorComentarioId = null;
            estadoDialogo.cursorComentarioFecha = null;
            estadoDialogo.contenedorComentarios.removeAllViews();
            estadoDialogo.botonCargarMasComentarios.setVisibility(View.GONE);
        }

        estadoDialogo.cargandoComentarios = true;
        estadoDialogo.botonCargarMasComentarios.setEnabled(false);

        FriendActivityRepository.getCommentsPage(
                        estadoDialogo.actividad.id,
                        TAMANO_PAGINA_COMENTARIOS,
                        reiniciar ? null : estadoDialogo.cursorComentarioId,
                        reiniciar ? null : estadoDialogo.cursorComentarioFecha
                )
                .addOnSuccessListener(pagina -> applyCommentPage(estadoDialogo, pagina, reiniciar))
                .addOnFailureListener(error -> {
                    String mensaje = error.getMessage() != null
                            ? error.getMessage()
                            : getString(R.string.actividades_error_comentarios_carga);
                    estadoDialogo.textoEstadoComentarios.setText(mensaje);
                    estadoDialogo.textoEstadoComentarios.setVisibility(View.VISIBLE);
                })
                .addOnCompleteListener(unused -> {
                    estadoDialogo.cargandoComentarios = false;
                    estadoDialogo.botonCargarMasComentarios.setEnabled(true);
                });
    }

    // Aplica comentarios nuevos al modal.
    private void applyCommentPage(
            DialogState estadoDialogo,
            FriendActivityCommentPageData pagina,
            boolean reiniciar
    ) {
        if (pagina == null) {
            return;
        }

        if (reiniciar) {
            estadoDialogo.contenedorComentarios.removeAllViews();
        }

        if (pagina.comentarios.isEmpty() && reiniciar) {
            estadoDialogo.textoEstadoComentarios.setText(getString(R.string.actividades_comentarios_vacio));
            estadoDialogo.textoEstadoComentarios.setVisibility(View.VISIBLE);
        } else {
            estadoDialogo.textoEstadoComentarios.setVisibility(View.GONE);
            for (FriendActivityCommentData comentario : pagina.comentarios) {
                estadoDialogo.contenedorComentarios.addView(buildCommentCard(estadoDialogo, comentario));
            }
        }

        estadoDialogo.cursorComentarioId = pagina.cursorId;
        estadoDialogo.cursorComentarioFecha = pagina.cursorFecha;
        estadoDialogo.botonCargarMasComentarios.setVisibility(
                pagina.hayMas ? View.VISIBLE : View.GONE
        );
    }

    // Crea la tarjeta visual de un comentario.
    private View buildCommentCard(DialogState estadoDialogo, FriendActivityCommentData comentario) {
        LinearLayout tarjeta = new LinearLayout(this);
        tarjeta.setOrientation(LinearLayout.VERTICAL);
        tarjeta.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));
        LinearLayout.LayoutParams paramsTarjeta = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsTarjeta.bottomMargin = dpToPx(12);
        tarjeta.setLayoutParams(paramsTarjeta);
        tarjeta.setBackgroundResource(R.drawable.bg_card_generic_premium);

        LinearLayout filaAutor = new LinearLayout(this);
        filaAutor.setOrientation(LinearLayout.HORIZONTAL);
        filaAutor.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        RoundedImageView imagenPerfil = ThematicListUiHelper.createCircularProfileImage(this, 44);
        LinearLayout.LayoutParams paramsImagen = new LinearLayout.LayoutParams(dpToPx(44), dpToPx(44));
        paramsImagen.setMarginEnd(dpToPx(8));
        imagenPerfil.setLayoutParams(paramsImagen);
        Bitmap bitmapPerfil = decodeDataUrl(comentario.fotoPerfilDataUrl);
        if (bitmapPerfil != null) {
            imagenPerfil.setImageBitmap(bitmapPerfil);
        }
        filaAutor.addView(imagenPerfil);

        LinearLayout columna = new LinearLayout(this);
        columna.setOrientation(LinearLayout.VERTICAL);
        columna.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView textoNick = new TextView(this);
        textoNick.setText(safeText(comentario.nick));
        textoNick.setTextSize(15f);
        textoNick.setTypeface(null, android.graphics.Typeface.BOLD);
        textoNick.setTextColor(getColor(R.color.primary_button_orange_flat));
        columna.addView(textoNick);

        TextView textoFecha = new TextView(this);
        textoFecha.setText(formatDateTime(comentario.fecha));
        textoFecha.setTextSize(12f);
        textoFecha.setTextColor(getColor(R.color.carousel_subtitle));
        columna.addView(textoFecha);

        filaAutor.addView(columna);

        if (TextUtils.equals(comentario.userId, uidActual)) {
            ContextThemeWrapper contextoDanger = new ContextThemeWrapper(this, R.style.Theme_Comiku_DangerButton);
            Button botonEliminar = new Button(contextoDanger, null, 0);
            LinearLayout.LayoutParams paramsBoton = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            paramsBoton.setMarginStart(dpToPx(8));
            botonEliminar.setLayoutParams(paramsBoton);
            botonEliminar.setText(getString(R.string.detalle_lista_boton_eliminar_comentario));
            botonEliminar.setAllCaps(false);
            botonEliminar.setBackgroundResource(R.drawable.bg_button_danger);
            botonEliminar.setBackgroundTintList(null);
            botonEliminar.setTextColor(getColorStateList(R.color.button_danger_text));
            botonEliminar.setTypeface(null, android.graphics.Typeface.BOLD);
            botonEliminar.setTextSize(15f);
            botonEliminar.setPadding(dpToPx(16), dpToPx(11), dpToPx(16), dpToPx(11));
            botonEliminar.setMinHeight(0);
            botonEliminar.setOnClickListener(v -> deleteCommentInDialog(estadoDialogo, comentario.id));
            filaAutor.addView(botonEliminar);
        }

        tarjeta.addView(filaAutor);

        TextView textoComentario = new TextView(this);
        LinearLayout.LayoutParams paramsComentario = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsComentario.topMargin = dpToPx(6);
        textoComentario.setLayoutParams(paramsComentario);
        textoComentario.setPadding(dpToPx(2), dpToPx(6), dpToPx(2), dpToPx(2));
        textoComentario.setText(safeText(comentario.texto));
        textoComentario.setTextSize(16f);
        textoComentario.setTextColor(getColor(R.color.carousel_subtitle));
        tarjeta.addView(textoComentario);

        if (!TextUtils.isEmpty(comentario.userId)) {
            textoNick.setOnClickListener(v -> openUserProfile(comentario.userId));
            imagenPerfil.setOnClickListener(v -> openUserProfile(comentario.userId));
        }

        return tarjeta;
    }

    // Elimina un comentario propio desde el modal.
    private void deleteCommentInDialog(DialogState estadoDialogo, String comentarioId) {
        if (TextUtils.isEmpty(comentarioId) || estadoDialogo.procesandoEliminarComentario) {
            return;
        }
        estadoDialogo.procesandoEliminarComentario = true;

        FriendActivityRepository.deleteComment(estadoDialogo.actividad.id, comentarioId, uidActual)
                .addOnSuccessListener(unused -> {
                    estadoDialogo.cantidadComentarios = Math.max(0, estadoDialogo.cantidadComentarios - 1);
                    renderDialogStats(estadoDialogo);
                    updateActivityStats(estadoDialogo.actividad.id, null, estadoDialogo.cantidadComentarios);
                    loadCommentsForDialog(estadoDialogo, true);
                })
                .addOnFailureListener(error -> {
                    String mensaje = error.getMessage() != null
                            ? error.getMessage()
                            : getString(R.string.actividades_error_eliminar_comentario);
                    estadoDialogo.textoEstadoComentarios.setText(mensaje);
                    estadoDialogo.textoEstadoComentarios.setVisibility(View.VISIBLE);
                })
                .addOnCompleteListener(unused -> estadoDialogo.procesandoEliminarComentario = false);
    }

    // Refresca texto de likes y comentarios en el modal.
    private void renderDialogStats(DialogState estadoDialogo) {
        estadoDialogo.botonLike.setActivated(estadoDialogo.likeActivo);
        estadoDialogo.botonLike.setText(String.valueOf(estadoDialogo.cantidadLikes));
        estadoDialogo.textoLikes.setVisibility(View.GONE);
        estadoDialogo.textoComentarios.setVisibility(View.GONE);
        estadoDialogo.textoLikes.setTextColor(Color.WHITE);
        estadoDialogo.textoComentarios.setTextColor(Color.WHITE);
        estadoDialogo.textoTituloComentarios.setTextColor(Color.WHITE);
        estadoDialogo.textoTituloComentarios.setText(
                getString(R.string.actividades_comentarios_titulo_con_total, estadoDialogo.cantidadComentarios)
        );
    }

    // Actualiza contadores en la lista principal cuando cambia una actividad.
    private void updateActivityStats(String actividadId, Integer likes, Integer comentarios) {
        for (int i = 0; i < actividades.size(); i++) {
            FriendActivityData item = actividades.get(i);
            if (!TextUtils.equals(item.id, actividadId)) {
                continue;
            }
            int likesActualizados = likes != null ? likes : item.cantidadLikes;
            int comentariosActualizados = comentarios != null ? comentarios : item.cantidadComentarios;
            actividades.set(i, new FriendActivityData(
                    item.id,
                    item.actorUid,
                    item.actorNick,
                    item.actorFotoPerfilDataUrl,
                    item.tipoActividad,
                    item.cantidadElementos,
                    item.tomos,
                    item.listas,
                    item.fecha,
                    likesActualizados,
                    comentariosActualizados
            ));
            break;
        }
        renderActivitiesList();
    }

    // Abre la actividad puntual cuando la pantalla llega desde una notificacion.
    private void openPendingActivityIfNeeded() {
        if (actividadPendienteAbierta || TextUtils.isEmpty(actividadPendienteId)) {
            return;
        }

        for (FriendActivityData actividad : actividades) {
            if (TextUtils.equals(actividadPendienteId, actividad.id)) {
                actividadPendienteAbierta = true;
                openActivityDialog(actividad);
                return;
            }
        }

        FriendActivityRepository.getActivityById(actividadPendienteId)
                .addOnSuccessListener(actividad -> {
                    if (actividad == null || actividadPendienteAbierta) {
                        return;
                    }
                    actividadPendienteAbierta = true;
                    openActivityDialog(actividad);
                });
    }

    // Crea una imagen de portada para lista y modal.
    private ImageView createCoverImage(String dataUrl, int anchoDp, int altoDp) {
        ImageView imagen = ThematicListUiHelper.createMiniCover(this, dataUrl, anchoDp, altoDp);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) imagen.getLayoutParams();
        params.rightMargin = 0;
        imagen.setLayoutParams(params);
        imagen.setBackgroundResource(R.drawable.bg_volume_cover_image);
        imagen.setClipToOutline(true);
        if (!(imagen instanceof RoundedImageView)) {
            imagen.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            ((RoundedImageView) imagen).setCornerRadius(dpToPx(10));
        }
        if (ThematicListUiHelper.decodeDataUrl(dataUrl) == null) {
            imagen.setImageResource(android.R.drawable.ic_menu_report_image);
        }
        return imagen;
    }

    // Crea una imagen redonda para autor de comentarios.
    private ImageView createCircleImage(String dataUrl, int tamanoDp) {
        ImageView imagen = new ImageView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dpToPx(tamanoDp), dpToPx(tamanoDp));
        imagen.setLayoutParams(params);
        imagen.setScaleType(ImageView.ScaleType.CENTER_CROP);

        Bitmap bitmap = decodeDataUrl(dataUrl);
        if (bitmap != null) {
            imagen.setImageBitmap(bitmap);
        } else {
            imagen.setImageResource(R.drawable.default_profile_picture);
        }
        return imagen;
    }

    // Abre detalle de tomo desde una portada en actividad.
    private void openVolumeDetail(String comicId, String tomoId) {
        if (TextUtils.isEmpty(comicId) || TextUtils.isEmpty(tomoId)) {
            return;
        }
        Intent pantalla = new Intent(this, VolumeDetailActivity.class);
        pantalla.putExtra(VolumeDetailActivity.EXTRA_COMIC_ID, comicId);
        pantalla.putExtra(VolumeDetailActivity.EXTRA_VOLUME_ID, tomoId);
        startActivity(pantalla);
    }

    // Abre detalle de lista tematica desde una actividad.
    private void openThematicListDetail(String listaId) {
        if (TextUtils.isEmpty(listaId)) {
            return;
        }
        Intent pantalla = new Intent(this, ThematicListDetailActivity.class);
        pantalla.putExtra(ThematicListDetailActivity.EXTRA_LIST_ID, listaId);
        startActivity(pantalla);
    }

    // Abre perfil publico de un usuario.
    private void openUserProfile(String uidPerfil) {
        if (TextUtils.isEmpty(uidPerfil)) {
            return;
        }

        FriendshipRepository.canOpenUserProfile(uidActual, uidPerfil)
                .addOnSuccessListener(canOpen -> {
                    if (!Boolean.TRUE.equals(canOpen)) {
                        ToastUtils.showTextToast(this, R.string.perfil_acceso_bloqueado, Toast.LENGTH_SHORT);
                        return;
                    }
                    Intent pantallaPerfil = new Intent(this, ProfileActivity.class);
                    pantallaPerfil.putExtra(ProfileActivity.EXTRA_USER_ID, uidPerfil);
                    startActivity(pantallaPerfil);
                })
                .addOnFailureListener(error ->
                        ToastUtils.showTextToast(this, R.string.error_perfil_carga, Toast.LENGTH_SHORT)
                );
    }

    // Convierte una fecha en texto legible para el usuario.
    private String formatDateTime(Date fecha) {
        if (fecha == null) {
            return getString(R.string.actividades_fecha_no_disponible);
        }
        SimpleDateFormat formato = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        formato.setTimeZone(ZONA_HORARIA_ACTIVIDADES);
        return formato.format(fecha);
    }

    // Convierte dataUrl a bitmap para mostrar imagenes.
    private Bitmap decodeDataUrl(String dataUrl) {
        return ThematicListUiHelper.decodeDataUrl(dataUrl);
    }

    // Convierte dp a pixeles para vistas dinamicas.
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // Devuelve una excepcion segura para fallas de tareas.
    private Exception getTaskError(Task<?> tarea, String mensaje) {
        Exception error = tarea.getException();
        return error != null ? error : new Exception(mensaje);
    }

    // Evita textos nulos en vistas de la pantalla.
    private String safeText(String texto) {
        return texto != null ? texto : "";
    }

    // Mantiene estado temporal del modal de actividad.
    private static final class DialogState {
        private AlertDialog dialogo;
        private FriendActivityData actividad;
        private TextView textoTitulo;
        private TextView textoFecha;
        private Button botonLike;
        private TextView textoLikes;
        private TextView textoComentarios;
        private TextView textoTituloComentarios;
        private LinearLayout contenedorDetalle;
        private EditText campoComentario;
        private Button botonEnviarComentario;
        private TextView textoEstadoComentarios;
        private LinearLayout contenedorComentarios;
        private Button botonCargarMasComentarios;
        private boolean likeActivo;
        private int cantidadLikes;
        private int cantidadComentarios;
        private String cursorComentarioId;
        private Date cursorComentarioFecha;
        private boolean cargandoComentarios;
        private boolean procesandoLike;
        private boolean procesandoComentario;
        private boolean procesandoEliminarComentario;
    }
}
