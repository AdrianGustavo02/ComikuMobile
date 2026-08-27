package com.example.comiku.screens;

import android.content.Intent;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.comiku.R;
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

public class ThematicListDetailActivity extends AppCompatActivity {

    public static final String EXTRA_LIST_ID = "extra_list_id";

    private LinearLayout contenedorWallpaper;
    private LinearLayout contenedorCreador;
    private TextView textoNombre;
    private TextView textoDescripcion;
    private TextView textoNickCreador;
    private TextView textoGuiaBadge;
    private Button botonMeGusta;
    private TextView textoLikes;
    private Button botonGuardarLista;
    private Button botonEditarLista;
    private android.widget.ProgressBar barraCarga;
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
        setContentView(R.layout.activity_thematic_list_detail);

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
        bindViews();
        setupToolbar();
        loadListDetail();
    }


    private void bindViews() {
        contenedorWallpaper = findViewById(R.id.contenedorWallpaperDetalle);
        contenedorCreador = findViewById(R.id.contenedorCreadorDetalle);
        textoNombre = findViewById(R.id.textoNombreListaDetalle);
        textoDescripcion = findViewById(R.id.textoDescripcionListaDetalle);
        textoNickCreador = findViewById(R.id.textoNickCreadorDetalle);
        textoGuiaBadge = findViewById(R.id.textoGuiaBadgeDetalle);
        botonMeGusta = findViewById(R.id.botonMeGustaDetalle);
        textoLikes = findViewById(R.id.textoLikesDetalle);
        botonGuardarLista = findViewById(R.id.botonGuardarListaDetalle);
        botonEditarLista = findViewById(R.id.botonEditarListaDetalle);
        barraCarga = findViewById(R.id.barraCargaDetalle);
        contenedorTomos = findViewById(R.id.contenedorTomosDetalle);
        textoEstado = findViewById(R.id.textoEstadoDetalle);
        contenedorComentarios = findViewById(R.id.contenedorComentarios);
        textoEstadoComentarios = findViewById(R.id.textoEstadoComentarios);
        campoComentario = findViewById(R.id.campoComentario);
        botonEnviarComentario = findViewById(R.id.botonEnviarComentario);
        textoErrorComentario = findViewById(R.id.textoErrorComentario);
    }


    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbarDetalleListaTematica);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.listas_tematicas_titulo));
        }
    }

    // Carga el detalle de la lista tematica desde Firestore.
    private void loadListDetail() {
        barraCarga.setVisibility(View.VISIBLE);
        textoEstado.setVisibility(View.GONE);
        contenedorWallpaper.removeAllViews();
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
        contenedorWallpaper.addView(ThematicListUiHelper.createWallpaperStrip(
                this,
                lista.fotosDePortadas,
                200,
                getString(R.string.listas_tematicas_sin_portadas)
        ));
        textoNombre.setText(lista.nombre);
        textoDescripcion.setText(lista.descripcion);
        textoLikes.setText(getString(R.string.listas_tematicas_likes, lista.cantidadLikes));
        textoGuiaBadge.setVisibility(lista.esGuiaDeLectura ? View.VISIBLE : View.GONE);

        if (!TextUtils.isEmpty(lista.userId)) {
            ThematicListRepository.getCreatorNick(lista.userId)
                    .addOnSuccessListener(nick -> {
                        textoNickCreador.setText(nick);
                        // Nick del creador es clickeable y lleva a su perfil.
                        textoNickCreador.setOnClickListener(v -> openUserProfile(lista.userId));
                        contenedorCreador.setOnClickListener(v -> openUserProfile(lista.userId));
                    })
                    .addOnFailureListener(error -> textoNickCreador.setText(getString(R.string.detalle_lista_creador_generico)));
        } else {
            textoNickCreador.setText(getString(R.string.detalle_lista_creador_generico));
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
                    textoLikes.setText(getString(R.string.listas_tematicas_likes, listaActual.cantidadLikes));
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
        botonMeGusta.setText(likeActivo
                ? getString(R.string.detalle_lista_me_gusta_activo)
                : getString(R.string.detalle_lista_me_gusta));
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
        List<Task<?>> tareas = new ArrayList<>();
        for (ThematicListVolumeData tomoLista : tomos) {
            Task<VolumeListItemData> tarea = buildVolumeItemData(tomoLista)
                    .addOnSuccessListener(item -> {
                        if (item != null) contenedorTomos.addView(buildVolumeCard(item));
                    });
            tareas.add(tarea);
        }
        Tasks.whenAll(tareas).addOnCompleteListener(task -> barraCarga.setVisibility(View.GONE));
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
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.addView(ThematicListUiHelper.createMiniCover(this, item.tomo.getPortadaDataUrl(), 60, 90));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        info.addView(ThematicListUiHelper.createTitle(this, item.getComicNombre(), 15f));

        TextView textoNumero = new TextView(this);
        textoNumero.setText(item.tomo.getNumeroFormateado());
        info.addView(textoNumero);

        card.addView(fila);
        fila.addView(info);
        card.setOnClickListener(v -> openVolumeDetail(item.comicId, item.tomoId));
        return card;
    }

    // Carga y muestra los comentarios de la lista.
    private void loadComments() {
        ThematicListRepository.getComments(listId)
                .addOnSuccessListener(comentarios -> {
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

    // Crea la card de un comentario con foto, nick y texto.
    private View buildCommentCard(ThematicListCommentData comentario) {
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dpToPx(12);
        card.setLayoutParams(cardParams);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        card.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);

        // foto de perfil y nick
        LinearLayout filaAutor = new LinearLayout(this);
        filaAutor.setOrientation(LinearLayout.HORIZONTAL);
        filaAutor.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView fotoPerfil = new ImageView(this);
        LinearLayout.LayoutParams fotoParams = new LinearLayout.LayoutParams(dpToPx(36), dpToPx(36));
        fotoParams.setMarginEnd(dpToPx(8));
        fotoPerfil.setLayoutParams(fotoParams);
        fotoPerfil.setScaleType(ImageView.ScaleType.CENTER_CROP);
        fotoPerfil.setImageResource(R.drawable.default_profile_picture);
        filaAutor.addView(fotoPerfil);

        LinearLayout columnaAutor = new LinearLayout(this);
        columnaAutor.setOrientation(LinearLayout.VERTICAL);
        columnaAutor.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Nick del comentador como elemento interactivo.
        TextView textoNick = new TextView(this);
        textoNick.setText(getString(R.string.detalle_lista_creador_generico));
        textoNick.setTextSize(13f);
        textoNick.setTypeface(null, android.graphics.Typeface.BOLD);
        textoNick.setTextColor(getResources().getColor(android.R.color.holo_blue_dark, getTheme()));
        columnaAutor.addView(textoNick);

        // Fecha del comentario.
        if (comentario.fechaComentario != null) {
            TextView textoFecha = new TextView(this);
            textoFecha.setText(formatCommentDate(comentario.fechaComentario));
            textoFecha.setTextSize(11f);
            textoFecha.setTextColor(getResources().getColor(android.R.color.darker_gray, getTheme()));
            columnaAutor.addView(textoFecha);
        }

        filaAutor.addView(columnaAutor);
        card.addView(filaAutor);

        // Texto del comentario.
        TextView textoComentario = new TextView(this);
        LinearLayout.LayoutParams textoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textoParams.topMargin = dpToPx(6);
        textoComentario.setLayoutParams(textoParams);
        textoComentario.setText(comentario.comentario);
        textoComentario.setTextSize(14f);
        card.addView(textoComentario);

        // Carga nick y foto del autor del comentario
        if (!TextUtils.isEmpty(comentario.userId)) {
            loadCommentAuthorData(comentario.userId, fotoPerfil, textoNick);
            // Click en nick lleva al perfil del comentador.
            textoNick.setOnClickListener(v -> openUserProfile(comentario.userId));
            fotoPerfil.setOnClickListener(v -> openUserProfile(comentario.userId));
        }

        // Boton eliminar solo visible para el dueño del comentario.
        FirebaseUser usuarioActual = FirebaseAuth.getInstance().getCurrentUser();
        if (usuarioActual != null && usuarioActual.getUid().equals(comentario.userId)) {
            Button botonEliminar = new Button(this);
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            btnParams.topMargin = dpToPx(4);
            botonEliminar.setLayoutParams(btnParams);
            botonEliminar.setText(getString(R.string.detalle_lista_boton_eliminar_comentario));
            botonEliminar.setOnClickListener(v -> confirmDeleteComment(comentario.id, usuarioActual.getUid()));
            card.addView(botonEliminar);
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
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.detalle_lista_confirmar_borrar_comentario_titulo))
                .setMessage(getString(R.string.detalle_lista_confirmar_borrar_comentario_mensaje))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> deleteComment(commentId, uid))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
                        Toast.makeText(this, R.string.perfil_acceso_bloqueado, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent intento = new Intent(this, ProfileActivity.class);
                    intento.putExtra(ProfileActivity.EXTRA_USER_ID, uid);
                    startActivity(intento);
                })
                .addOnFailureListener(error ->
                        Toast.makeText(this, R.string.error_perfil_carga, Toast.LENGTH_SHORT).show());
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
