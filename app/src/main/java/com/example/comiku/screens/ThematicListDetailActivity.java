package com.example.comiku.screens;

import android.content.Intent;
import android.view.ContextThemeWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.example.comiku.R;
import com.example.comiku.core.ui.DeleteConfirmDialogComponent;
import com.example.comiku.core.ui.ToastUtils;
import com.example.comiku.core.ui.ThematicListUiHelper;
import com.example.comiku.data.model.ComicDetailData;
import com.example.comiku.data.model.ThematicListCommentData;
import com.example.comiku.data.model.ThematicListData;
import com.example.comiku.data.model.ThematicListVolumeData;
import com.example.comiku.data.model.VolumeDetailData;
import com.example.comiku.data.repository.FriendshipRepository;
import com.example.comiku.data.repository.ComicDetailRepository;
import com.example.comiku.data.repository.ThematicListRepository;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ThematicListDetailActivity extends BasePlainScreenActivity {

    public static final String EXTRA_LIST_ID = "extra_list_id";

    private LinearLayout contenedorCreador;
    private TextView textoNombre;
    private TextView textoDescripcion;
    private TextView textoNickCreador;
    private ImageView iconoFlechaCreador;
    private TextView textoGuiaBadge;
    private Button botonMeGusta;
    private TextView textoComentariosCantidad;
    private Button botonGuardarLista;
    private Button botonEditarLista;
    private android.widget.ProgressBar barraCarga;
    private TextView textoTituloTomos;
    private TextView textoTituloComentarios;
    private LinearLayout contenedorTomos;
    private TextView textoEstado;
    private LinearLayout contenedorComentarios;
    private TextView textoEstadoComentarios;
    private EditText campoComentario;
    private Button botonEnviarComentario;
    private TextView textoErrorComentario;

    private String listId;
    private String uidActual;
    private ThematicListData listaActual;
    private boolean likeActivo;
    private boolean guardadoActivo;

    private final ActivityResultLauncher<Intent> lanzadorEdicion = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            resultado -> {
                if (resultado.getResultCode() == RESULT_OK) {
                    contenedorTomos.removeAllViews();
                    loadListDetail();
                }
            }
    );

    // Inicia la pantalla de detalle de lista tematica.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        listId = getIntent().getStringExtra(EXTRA_LIST_ID);
        if (TextUtils.isEmpty(listId)) {
            finish();
            return;
        }

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            finish();
            return;
        }
        uidActual = usuario.getUid();
        setupPlainScreenShell(R.layout.activity_thematic_list_detail);
        bindViews();
        loadListDetail();
    }


    private void bindViews() {
        contenedorCreador = findViewById(R.id.contenedorCreadorDetalle);
        textoNombre = findViewById(R.id.textoNombreListaDetalle);
        textoDescripcion = findViewById(R.id.textoDescripcionListaDetalle);
        textoNickCreador = findViewById(R.id.textoNickCreadorDetalle);
        iconoFlechaCreador = findViewById(R.id.iconoFlechaCreadorDetalle);
        textoGuiaBadge = findViewById(R.id.textoGuiaBadgeDetalle);
        botonMeGusta = findViewById(R.id.botonMeGustaDetalle);
        textoComentariosCantidad = findViewById(R.id.textoComentariosCantidadDetalle);
        botonMeGusta.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_like_outline, 0, 0, 0);
        botonMeGusta.setCompoundDrawablePadding(dpToPx(6));
        botonGuardarLista = findViewById(R.id.botonGuardarListaDetalle);
        botonEditarLista = findViewById(R.id.botonEditarListaDetalle);
        barraCarga = findViewById(R.id.barraCargaDetalle);
        textoTituloTomos = findViewById(R.id.textoTituloTomosDetalle);
        textoTituloComentarios = findViewById(R.id.textoTituloComentariosDetalle);
        contenedorTomos = findViewById(R.id.contenedorTomosDetalle);
        textoEstado = findViewById(R.id.textoEstadoDetalle);
        contenedorComentarios = findViewById(R.id.contenedorComentarios);
        textoEstadoComentarios = findViewById(R.id.textoEstadoComentarios);
        campoComentario = findViewById(R.id.campoComentario);
        botonEnviarComentario = findViewById(R.id.botonEnviarComentario);
        textoErrorComentario = findViewById(R.id.textoErrorComentario);
    }


    // Carga el detalle de la lista tematica desde Firestore.
    private void loadListDetail() {
        barraCarga.setVisibility(View.VISIBLE);
        textoEstado.setVisibility(View.GONE);
        contenedorTomos.removeAllViews();
        contenedorComentarios.removeAllViews();

        ThematicListRepository.getThematicListById(listId)
                .addOnSuccessListener(lista -> {
                    if (lista == null) {
                        barraCarga.setVisibility(View.GONE);
                        textoEstado.setText(getString(R.string.detalle_lista_no_encontrada));
                        textoEstado.setVisibility(View.VISIBLE);
                        return;
                    }
                    listaActual = lista;
                    if (!TextUtils.isEmpty(lista.userId)) {
                        FriendshipRepository.canOpenUserProfile(uidActual, lista.userId)
                                .addOnSuccessListener(canOpen -> {
                                    if (!Boolean.TRUE.equals(canOpen)) {
                                        textoEstado.setText(getString(R.string.perfil_acceso_bloqueado));
                                        textoEstado.setVisibility(View.VISIBLE);
                                        barraCarga.setVisibility(View.GONE);
                                        return;
                                    }
                                    renderListDetail(lista);
                                    loadUserActions();
                                    loadVolumes();
                                    loadComments();
                                })
                                .addOnFailureListener(error -> {
                                    barraCarga.setVisibility(View.GONE);
                                    textoEstado.setText(getString(R.string.detalle_lista_error_carga));
                                    textoEstado.setVisibility(View.VISIBLE);
                                });
                        return;
                    }
                    renderListDetail(lista);
                    loadUserActions();
                    loadVolumes();
                    loadComments();
                })
                .addOnFailureListener(error -> {
                    barraCarga.setVisibility(View.GONE);
                    textoEstado.setText(getString(R.string.detalle_lista_error_carga));
                    textoEstado.setVisibility(View.VISIBLE);
                });
    }

    // Muestra los datos principales de la lista.
    private void renderListDetail(ThematicListData lista) {
        textoNombre.setText(lista.nombre);
        textoDescripcion.setText(lista.descripcion);
        botonMeGusta.setText(String.valueOf(lista.cantidadLikes));
        textoComentariosCantidad.setText(String.valueOf(lista.cantidadComentarios));
        updateCommentsTitle(lista.cantidadComentarios);
        textoGuiaBadge.setText(getString(
                R.string.detalle_lista_guia_badge_formato,
                getString(R.string.listas_tematicas_guia_badge)
        ));
        textoGuiaBadge.setVisibility(lista.esGuiaDeLectura ? View.VISIBLE : View.GONE);

        if (!TextUtils.isEmpty(lista.userId)) {
            ThematicListRepository.getCreatorNick(lista.userId)
                    .addOnSuccessListener(nick -> {
                        textoNickCreador.setText(nick);
                        iconoFlechaCreador.setVisibility(View.VISIBLE);
                        // Nick del creador es clickeable y lleva a su perfil.
                        textoNickCreador.setOnClickListener(v -> openUserProfile(lista.userId));
                        contenedorCreador.setOnClickListener(v -> openUserProfile(lista.userId));
                    })
                    .addOnFailureListener(error -> {
                        textoNickCreador.setText(getString(R.string.detalle_lista_creador_generico));
                        iconoFlechaCreador.setVisibility(View.GONE);
                    });
        } else {
            textoNickCreador.setText(getString(R.string.detalle_lista_creador_generico));
            iconoFlechaCreador.setVisibility(View.GONE);
        }

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        boolean esCreador = usuario != null && TextUtils.equals(usuario.getUid(), lista.userId);
        botonEditarLista.setVisibility(esCreador ? View.VISIBLE : View.GONE);
        botonGuardarLista.setVisibility(esCreador ? View.GONE : View.VISIBLE);

        botonEditarLista.setOnClickListener(v -> openEditList());
        botonMeGusta.setOnClickListener(v -> toggleLike());
        botonGuardarLista.setOnClickListener(v -> toggleSave());

        botonEnviarComentario.setOnClickListener(v -> submitComment());
    }

    // Carga el estado de like y guardado del usuario actual.
    private void loadUserActions() {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            updateLikeButton();
            updateSaveButton();
            return;
        }

        ThematicListRepository.getUserLikeStatus(listId, usuario.getUid())
                .addOnSuccessListener(liked -> {
                    likeActivo = liked;
                    updateLikeButton();
                });

        ThematicListRepository.getUserSavedListStatus(listId, usuario.getUid())
                .addOnSuccessListener(saved -> {
                    guardadoActivo = saved;
                    updateSaveButton();
                });
    }

    // Alterna el like del usuario.
    private void toggleLike() {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null || listaActual == null) {
            return;
        }

        botonMeGusta.setEnabled(false);
        ThematicListRepository.toggleLikeForList(listId, usuario.getUid())
                .addOnSuccessListener(liked -> {
                    likeActivo = liked;
                    long cantidadLikesNueva = listaActual.cantidadLikes + (liked ? 1 : -1);
                    if (cantidadLikesNueva < 0) cantidadLikesNueva = 0;
                    listaActual = new ThematicListData(
                            listaActual.id, listaActual.userId, listaActual.nombre,
                            listaActual.descripcion, cantidadLikesNueva,
                            listaActual.cantidadComentarios, listaActual.fechaCreacion,
                            listaActual.esGuiaDeLectura, listaActual.fotosDePortadas
                    );
                    botonMeGusta.setText(String.valueOf(listaActual.cantidadLikes));
                    updateLikeButton();
                    botonMeGusta.setEnabled(true);
                })
                .addOnFailureListener(error -> botonMeGusta.setEnabled(true));
    }

    // Alterna si el usuario guardo la lista.
    private void toggleSave() {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) return;

        botonGuardarLista.setEnabled(false);
        ThematicListRepository.toggleSaveListForUser(listId, usuario.getUid())
                .addOnSuccessListener(saved -> {
                    guardadoActivo = saved;
                    updateSaveButton();
                    botonGuardarLista.setEnabled(true);
                })
                .addOnFailureListener(error -> botonGuardarLista.setEnabled(true));
    }

    // Actualiza el texto del boton de like.
    private void updateLikeButton() {
        botonMeGusta.setActivated(likeActivo);
    }

    // Actualiza el texto del boton de guardado.
    private void updateSaveButton() {
        botonGuardarLista.setText(guardadoActivo
                ? getString(R.string.detalle_lista_guardada_activa)
                : getString(R.string.detalle_lista_guardar_lista));
    }

    // Carga los tomos de la lista y arma sus cards
    private void loadVolumes() {
        ThematicListRepository.getListVolumes(listId)
                .addOnSuccessListener(tomos -> {
                    int cantidadTomos = tomos != null ? tomos.size() : 0;
                    textoTituloTomos.setText(getString(R.string.detalle_lista_tomos_titulo_formato, cantidadTomos));
                    if (tomos == null || tomos.isEmpty()) {
                        barraCarga.setVisibility(View.GONE);
                        textoEstado.setText(getString(R.string.detalle_lista_sin_tomos));
                        textoEstado.setVisibility(View.VISIBLE);
                        return;
                    }
                    renderVolumes(tomos);
                })
                .addOnFailureListener(error -> {
                    barraCarga.setVisibility(View.GONE);
                    textoEstado.setText(getString(R.string.detalle_lista_error_tomos));
                    textoEstado.setVisibility(View.VISIBLE);
                });
    }

    // Carga la informacion de cada tomo.
    private void renderVolumes(List<ThematicListVolumeData> tomos) {
        List<Task<VolumeListItemData>> tareas = new ArrayList<>();
        for (ThematicListVolumeData tomoLista : tomos) {
            Task<VolumeListItemData> tarea = buildVolumeItemData(tomoLista);
            tareas.add(tarea);
        }
        Tasks.whenAllSuccess(tareas)
                .addOnSuccessListener(items -> {
                    List<VolumeListItemData> itemsValidos = new ArrayList<>();
                    for (Object item : items) {
                        if (item instanceof VolumeListItemData) {
                            itemsValidos.add((VolumeListItemData) item);
                        }
                    }
                    renderVolumeRows(itemsValidos);
                    barraCarga.setVisibility(View.GONE);
                })
                .addOnFailureListener(error -> {
                    barraCarga.setVisibility(View.GONE);
                    textoEstado.setText(getString(R.string.detalle_lista_error_tomos));
                    textoEstado.setVisibility(View.VISIBLE);
                });
    }

    // Dibuja los tomos en filas de dos cards.
    private void renderVolumeRows(List<VolumeListItemData> items) {
        int indice = 0;
        while (indice < items.size()) {
            LinearLayout fila = new LinearLayout(this);
            fila.setOrientation(LinearLayout.HORIZONTAL);
            fila.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            fila.setPadding(dpToPx(4), 0, dpToPx(4), dpToPx(8));

            int columnas = Math.min(2, items.size() - indice);
            for (int columna = 0; columna < columnas; columna++) {
                View tarjeta = buildVolumeCard(items.get(indice + columna));
                LinearLayout.LayoutParams paramsTarjeta = new LinearLayout.LayoutParams(
                        columnas == 1 ? dpToPx(160) : 0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        columnas == 1 ? 0f : 1f
                );
                paramsTarjeta.leftMargin = columna == 0 ? 0 : dpToPx(8);
                paramsTarjeta.rightMargin = columna == 0 ? dpToPx(8) : 0;
                tarjeta.setLayoutParams(paramsTarjeta);
                tarjeta.setMinimumHeight(dpToPx(260));
                fila.addView(tarjeta);
            }

            contenedorTomos.addView(fila);
            indice += columnas;
        }
    }

    // Construye los datos de una card de tomo.
    private Task<VolumeListItemData> buildVolumeItemData(ThematicListVolumeData tomoLista) {
        return ComicDetailRepository.getVolumeDetail(tomoLista.comicId, tomoLista.tomoId)
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        return Tasks.forResult(null);
                    }
                    VolumeDetailData tomo = task.getResult();
                    return ComicDetailRepository.getComicDetail(tomoLista.comicId)
                            .continueWith(taskComic -> {
                                ComicDetailData comic = taskComic.isSuccessful() ? taskComic.getResult() : null;
                                return new VolumeListItemData(tomoLista.comicId, tomoLista.tomoId, comic, tomo);
                            });
                });
    }

    // Crea la card de un tomo.
    private View buildVolumeCard(VolumeListItemData item) {
        LinearLayout card = ThematicListUiHelper.createCardContainer(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(getDrawable(R.drawable.bg_wishlist_volume_card));
        card.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));
        card.setClickable(true);
        card.setFocusable(true);

        android.widget.FrameLayout contenedorPortada = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams paramsContenedor = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(220)
        );
        paramsContenedor.bottomMargin = dpToPx(10);
        contenedorPortada.setLayoutParams(paramsContenedor);
        contenedorPortada.setClipToOutline(true);
        contenedorPortada.setClipChildren(true);
        android.graphics.drawable.GradientDrawable fondoPortada = new android.graphics.drawable.GradientDrawable();
        fondoPortada.setColor(android.graphics.Color.TRANSPARENT);
        fondoPortada.setCornerRadius(dpToPx(12));
        contenedorPortada.setBackground(fondoPortada);

        RoundedImageView imagenPortada = new RoundedImageView(this);
        android.widget.FrameLayout.LayoutParams paramsImagen = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        imagenPortada.setLayoutParams(paramsImagen);
        imagenPortada.setCornerRadius(dpToPx(12));
        imagenPortada.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        imagenPortada.setAdjustViewBounds(true);
        Bitmap bitmap = decodeDataUrl(item.tomo != null ? item.tomo.getPortadaDataUrl() : null);
        if (bitmap != null) {
            imagenPortada.setImageBitmap(bitmap);
        } else {
            imagenPortada.setImageResource(R.drawable.default_profile_picture);
        }
        contenedorPortada.addView(imagenPortada);
        card.addView(contenedorPortada);

        TextView textoComic = new TextView(this);
        textoComic.setText(item.getComicNombre());
        textoComic.setTextSize(15f);
        textoComic.setTypeface(null, android.graphics.Typeface.BOLD);
        textoComic.setMaxLines(3);
        textoComic.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textoComic.setTextColor(getColor(android.R.color.white));
        card.addView(textoComic);

        TextView textoTomo = new TextView(this);
        textoTomo.setText(item.tomo != null ? item.tomo.getNumeroFormateado() : "");
        textoTomo.setTextSize(12f);
        textoTomo.setMaxLines(2);
        textoTomo.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textoTomo.setTextColor(getColor(R.color.carousel_subtitle));
        textoTomo.setPadding(0, dpToPx(4), 0, 0);
        card.addView(textoTomo);

        TextView textoIsbn = new TextView(this);
        textoIsbn.setText("ISBN: " + (item.tomo != null && item.tomo.isbn != null
                ? item.tomo.isbn
                : "No definido"));
        textoIsbn.setTextSize(12f);
        textoIsbn.setMaxLines(2);
        textoIsbn.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textoIsbn.setTextColor(getColor(R.color.carousel_subtitle));
        textoIsbn.setPadding(0, dpToPx(2), 0, 0);
        card.addView(textoIsbn);

        card.setOnClickListener(v -> openVolumeDetail(item.comicId, item.tomoId));
        return card;
    }

    // Carga y muestra los comentarios de la lista.
    private void loadComments() {
        ThematicListRepository.getComments(listId)
                .addOnSuccessListener(comentarios -> {
                    int cantidadComentarios = comentarios != null ? comentarios.size() : 0;
                    textoComentariosCantidad.setText(String.valueOf(cantidadComentarios));
                    updateCommentsTitle(cantidadComentarios);
                    if (listaActual != null) {
                        listaActual = new ThematicListData(
                                listaActual.id, listaActual.userId, listaActual.nombre,
                                listaActual.descripcion, listaActual.cantidadLikes,
                                cantidadComentarios, listaActual.fechaCreacion,
                                listaActual.esGuiaDeLectura, listaActual.fotosDePortadas
                        );
                    }
                    contenedorComentarios.removeAllViews();
                    if (comentarios == null || comentarios.isEmpty()) {
                        textoEstadoComentarios.setText(getString(R.string.detalle_lista_sin_comentarios));
                        textoEstadoComentarios.setVisibility(View.VISIBLE);
                        return;
                    }
                    textoEstadoComentarios.setVisibility(View.GONE);
                    for (ThematicListCommentData comentario : comentarios) {
                        contenedorComentarios.addView(buildCommentCard(comentario));
                    }
                })
                .addOnFailureListener(error -> {
                    textoEstadoComentarios.setText(getString(R.string.detalle_lista_error_comentarios));
                    textoEstadoComentarios.setVisibility(View.VISIBLE);
                });
    }

    // Actualiza el titulo de comentarios con el total actual
    private void updateCommentsTitle(long cantidadComentarios) {
        textoTituloComentarios.setText(
                getString(R.string.detalle_lista_comentarios_titulo_formato, cantidadComentarios)
        );
    }

    // Crea la card de un comentario con foto, nick y texto.
    private View buildCommentCard(ThematicListCommentData comentario) {
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dpToPx(12);
        card.setLayoutParams(cardParams);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));
        card.setBackgroundResource(R.drawable.bg_card_generic_premium);

        // Foto de perfil y nick
        LinearLayout filaAutor = new LinearLayout(this);
        filaAutor.setOrientation(LinearLayout.HORIZONTAL);
        filaAutor.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        RoundedImageView fotoPerfil = ThematicListUiHelper.createCircularProfileImage(this, 44);
        LinearLayout.LayoutParams fotoParams = new LinearLayout.LayoutParams(dpToPx(44), dpToPx(44));
        fotoParams.setMarginEnd(dpToPx(8));
        fotoPerfil.setLayoutParams(fotoParams);
        filaAutor.addView(fotoPerfil);

        LinearLayout columnaAutor = new LinearLayout(this);
        columnaAutor.setOrientation(LinearLayout.VERTICAL);
        columnaAutor.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Nick del comentador como elemento interactivo.
        TextView textoNick = new TextView(this);
        textoNick.setText(getString(R.string.detalle_lista_creador_generico));
        textoNick.setTextSize(15f);
        textoNick.setTypeface(null, android.graphics.Typeface.BOLD);
        textoNick.setTextColor(getColor(R.color.primary_button_orange_flat));
        textoNick.setPaintFlags(textoNick.getPaintFlags() & (~android.graphics.Paint.UNDERLINE_TEXT_FLAG));
        columnaAutor.addView(textoNick);

        // Fecha del comentario.
        if (comentario.fechaComentario != null) {
            TextView textoFecha = new TextView(this);
            textoFecha.setText(formatCommentDate(comentario.fechaComentario));
            textoFecha.setTextSize(12f);
            textoFecha.setTextColor(getColor(R.color.carousel_subtitle));
            columnaAutor.addView(textoFecha);
        }

        filaAutor.addView(columnaAutor);

        // Boton eliminar solo visible para el comentador
        FirebaseUser usuarioActual = FirebaseAuth.getInstance().getCurrentUser();
        if (usuarioActual != null && usuarioActual.getUid().equals(comentario.userId)) {
            ContextThemeWrapper contextoDanger = new ContextThemeWrapper(this, R.style.Theme_Comiku_DangerButton);
            Button botonEliminar = new Button(contextoDanger, null, 0);
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            btnParams.setMarginStart(dpToPx(8));
            botonEliminar.setLayoutParams(btnParams);
            botonEliminar.setText(getString(R.string.detalle_lista_boton_eliminar_comentario));
            botonEliminar.setOnClickListener(v -> confirmDeleteComment(comentario.id, usuarioActual.getUid()));
            filaAutor.addView(botonEliminar);
        }

        card.addView(filaAutor);

        // Texto del comentario.
        TextView textoComentario = new TextView(this);
        LinearLayout.LayoutParams textoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textoParams.topMargin = dpToPx(6);
        textoComentario.setLayoutParams(textoParams);
        textoComentario.setPadding(dpToPx(2), dpToPx(6), dpToPx(2), dpToPx(2));
        textoComentario.setText(comentario.comentario);
        textoComentario.setTextSize(16f);
        textoComentario.setTextColor(getColor(R.color.carousel_subtitle));
        card.addView(textoComentario);

        // Carga nick y foto del autor del comentario
        if (!TextUtils.isEmpty(comentario.userId)) {
            loadCommentAuthorData(comentario.userId, fotoPerfil, textoNick);
            // Click en nick lleva al perfil del comentador.
            textoNick.setOnClickListener(v -> openUserProfile(comentario.userId));
            fotoPerfil.setOnClickListener(v -> openUserProfile(comentario.userId));
        }

        return card;
    }

    // Carga nick y foto de perfil de un comentador
    private void loadCommentAuthorData(String uid, ImageView imagenPerfil, TextView textoNick) {
        FirebaseFirestore firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        firestore.collection("usuario").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) return;
                    String nick = doc.getString("Nick");
                    if (!TextUtils.isEmpty(nick)) textoNick.setText(nick);

                    Object fotoPerfil = doc.get("FotoPerfil");
                    if (fotoPerfil instanceof java.util.Map) {
                        Object dataUrl = ((java.util.Map<?, ?>) fotoPerfil).get("dataUrl");
                        if (dataUrl != null) {
                            Bitmap bitmap = decodeDataUrl(String.valueOf(dataUrl));
                            if (bitmap != null) imagenPerfil.setImageBitmap(bitmap);
                        }
                    }
                });
    }

    // Muestra un dialogo de confirmacion antes de eliminar el comentario.
    private void confirmDeleteComment(String commentId, String uid) {
        DeleteConfirmDialogComponent dialogoConfirmacion = new DeleteConfirmDialogComponent(
                this,
                getString(R.string.detalle_lista_confirmar_borrar_comentario_titulo),
                getString(R.string.detalle_lista_confirmar_borrar_comentario_mensaje),
                () -> deleteComment(commentId, uid)
        );
        dialogoConfirmacion.show();
    }

    // Elimina un comentario y recarga la lista.
    private void deleteComment(String commentId, String uid) {
        ThematicListRepository.deleteComment(listId, commentId, uid)
                .addOnSuccessListener(v -> loadComments())
                .addOnFailureListener(error -> {
                    textoErrorComentario.setText(getString(R.string.detalle_lista_error_borrar_comentario));
                    textoErrorComentario.setVisibility(View.VISIBLE);
                });
    }

    // Valida y envia un nuevo comentario.
    private void submitComment() {
        textoErrorComentario.setVisibility(View.GONE);
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) return;

        String texto = campoComentario.getText() != null
                ? campoComentario.getText().toString().trim() : "";
        // Sanitiza caracteres prohibidos.
        texto = sanitize(texto);

        if (TextUtils.isEmpty(texto)) {
            textoErrorComentario.setText(getString(R.string.detalle_lista_comentario_vacio));
            textoErrorComentario.setVisibility(View.VISIBLE);
            return;
        }

        botonEnviarComentario.setEnabled(false);
        String textoFinal = texto;
        ThematicListRepository.addComment(listId, usuario.getUid(), textoFinal)
                .addOnSuccessListener(v -> {
                    campoComentario.setText("");
                    botonEnviarComentario.setEnabled(true);
                    loadComments();
                })
                .addOnFailureListener(error -> {
                    botonEnviarComentario.setEnabled(true);
                    textoErrorComentario.setText(getString(R.string.detalle_lista_error_agregar_comentario));
                    textoErrorComentario.setVisibility(View.VISIBLE);
                });
    }

    // Abre el perfil de un usuario por su UID.
    private void openUserProfile(String uid) {
        if (TextUtils.isEmpty(uid)) return;
        FriendshipRepository.canOpenUserProfile(uidActual, uid)
                .addOnSuccessListener(canOpen -> {
                    if (!Boolean.TRUE.equals(canOpen)) {
                        ToastUtils.showTextToast(this, R.string.perfil_acceso_bloqueado, Toast.LENGTH_SHORT);
                        return;
                    }
                    Intent intento = new Intent(this, ProfileActivity.class);
                    intento.putExtra(ProfileActivity.EXTRA_USER_ID, uid);
                    startActivity(intento);
                })
                .addOnFailureListener(error ->
                        ToastUtils.showTextToast(this, R.string.error_perfil_carga, Toast.LENGTH_SHORT)
                );
    }

    // Abre la pantalla de edicion de la lista.
    private void openEditList() {
        Intent intento = new Intent(this, CreateThematicListActivity.class);
        intento.putExtra(CreateThematicListActivity.EXTRA_LIST_ID, listId);
        lanzadorEdicion.launch(intento);
    }

    // Abre el detalle del tomo elegido.
    private void openVolumeDetail(String comicId, String tomoId) {
        Intent intento = new Intent(this, VolumeDetailActivity.class);
        intento.putExtra(VolumeDetailActivity.EXTRA_COMIC_ID, comicId);
        intento.putExtra(VolumeDetailActivity.EXTRA_VOLUME_ID, tomoId);
        startActivity(intento);
    }

    // Formatea la fecha de un comentario para mostrarla.
    private String formatCommentDate(java.util.Date fecha) {
        try {
            return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(fecha);
        } catch (Exception ignored) {
            return "";
        }
    }

    // Remueve los caracteres prohibidos del texto ingresado.
    private String sanitize(String texto) {
        if (texto == null) return "";
        return texto.replaceAll("[@#$^&*{}\\[\\]<>]", "");
    }

    // Convierte dataUrl a bitmap para fotos de perfil de comentadores.
    private Bitmap decodeDataUrl(String dataUrl) {
        if (TextUtils.isEmpty(dataUrl)) return null;
        int indiceComa = dataUrl.indexOf(',');
        if (indiceComa < 0 || indiceComa >= dataUrl.length() - 1) return null;
        try {
            byte[] bytes = Base64.decode(dataUrl.substring(indiceComa + 1), Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception ignored) {
            return null;
        }
    }

    // Convierte dp a pixeles.
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // Navega hacia atras al presionar la flecha del toolbar.
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // Agrupa los datos necesarios para una card de tomo.
    private static final class VolumeListItemData {
        private final String comicId;
        private final String tomoId;
        private final ComicDetailData comic;
        private final VolumeDetailData tomo;

        private VolumeListItemData(String comicId, String tomoId, ComicDetailData comic, VolumeDetailData tomo) {
            this.comicId = comicId;
            this.tomoId = tomoId;
            this.comic = comic;
            this.tomo = tomo;
        }

        private String getComicNombre() {
            return comic != null && !TextUtils.isEmpty(comic.nombre) ? comic.nombre : "";
        }
    }
}
