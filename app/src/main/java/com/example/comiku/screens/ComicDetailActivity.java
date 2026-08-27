package com.example.comiku.screens;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.example.comiku.R;
import com.example.comiku.core.ui.MissingVolumeCarouselComponent;
import com.example.comiku.core.ui.ReportFormComponent;
import com.example.comiku.data.model.ComicDetailData;
import com.example.comiku.data.model.ComicMissingVolumesData;
import com.example.comiku.data.model.ComicReviewData;
import com.example.comiku.data.model.ComicReviewPageData;
import com.example.comiku.data.model.MissingVolumeData;
import com.example.comiku.data.model.VolumeDetailData;
import com.example.comiku.data.repository.ComicDetailRepository;
import com.example.comiku.data.repository.ComicReviewRepository;
import com.example.comiku.data.repository.ReportRepository;
import com.example.comiku.data.repository.UserShelfRepository;
import com.example.comiku.data.repository.FriendshipRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ComicDetailActivity extends AppCompatActivity {
    public static final String EXTRA_COMIC_ID = "extra_comic_id";

    private TextView textoNombreComic;
    private TextView textoAutores;
    private TextView textoEditorial;
    private TextView textoEstado;
    private TextView textoFormato;
    private TextView textoGeneros;
    private TextView textoDescripcion;
    private TextView textoCalificacion;
    private Button botonReportarComic;
    private GridView gridPortadasTomos;
    private TextView textoTituloTomosFaltantes;
    private LinearLayout contenedorCarruselTomosFaltantes;
    private ImageButton botonIzquierdaTomosFaltantes;
    private ImageButton botonDerechaTomosFaltantes;
    private HorizontalScrollView scrollTomosFaltantes;
    private LinearLayout contenedorTomosFaltantes;
    private TextView textoEstadoTomosFaltantes;
    private ProgressBar barraCarga;
    private TextView textoError;

    // Vistas de resenas.
    private LinearLayout panelFormularioResena;
    private LinearLayout contenedorEstrellas;
    private EditText campoComentarioResena;
    private TextView textoErrorResena;
    private Button botonGuardarResena;
    private Button botonEliminarResena;
    private TextView textoSinPermisosResena;
    private LinearLayout contenedorListaResenas;
    private Button botonCargarMasResenas;
    private TextView textoEstadoResenas;

    private String comicId;
    private String uidUsuario;
    private List<VolumeDetailData> tomosDelComic = new ArrayList<>();
    private MissingVolumeCarouselComponent componenteCarruselFaltantes;
    private VolumePortadaAdapter adaptadorPortadas;
    private ReportFormComponent reporteFormularioActual;
    private final ActivityResultLauncher<String> selectorCapturaReporte = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (reporteFormularioActual != null && uri != null) {
                    reporteFormularioActual.handleImageSelected(uri);
                }
            }
    );

    // Estado de resenas.
    private ComicReviewData resenaUsuario = null;
    private int calificacionSeleccionada = 0;
    private String ultimoIdResena = null;
    private boolean hayMasResenas = false;
    private final List<TextView> botonesEstrellas = new ArrayList<>();

    // Inicializa la pantalla de detalles del comic.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }

        comicId = getIntent().getStringExtra(EXTRA_COMIC_ID);
        if (TextUtils.isEmpty(comicId)) {
            finish();
            return;
        }

        uidUsuario = FirebaseAuth.getInstance().getCurrentUser().getUid();
        setContentView(R.layout.activity_comic_detail);
        bindViews();
        componenteCarruselFaltantes = new MissingVolumeCarouselComponent(this);
        setupMissingCarouselScroll();
        setupStarSelector();
        setupReportAction();
        loadComicDetail();
    }

    // Refresca tomos al volver para captar cambios del catalogo y biblioteca.
    @Override
    protected void onResume() {
        super.onResume();
        if (!TextUtils.isEmpty(comicId) && !tomosDelComic.isEmpty()) {
            loadVolumesList();
        }
    }

    // Vincula las vistas del layout.
    private void bindViews() {
        textoNombreComic = findViewById(R.id.textoNombreComicDetail);
        textoAutores = findViewById(R.id.textoAutoresComicDetail);
        textoEditorial = findViewById(R.id.textoEditorialComicDetail);
        textoEstado = findViewById(R.id.textoEstadoComicDetail);
        textoFormato = findViewById(R.id.textoFormatoComicDetail);
        textoGeneros = findViewById(R.id.textoGenerosComicDetail);
        textoDescripcion = findViewById(R.id.textoDescripcionComicDetail);
        textoCalificacion = findViewById(R.id.textoCalificacionComicDetail);
        botonReportarComic = findViewById(R.id.botonReportarComicDetail);
        gridPortadasTomos = findViewById(R.id.gridPortadasTomos);
        textoTituloTomosFaltantes = findViewById(R.id.textoTituloTomosFaltantesComicDetail);
        contenedorCarruselTomosFaltantes = findViewById(R.id.contenedorCarruselTomosFaltantesComicDetail);
        botonIzquierdaTomosFaltantes = findViewById(R.id.botonIzquierdaTomosFaltantesComicDetail);
        botonDerechaTomosFaltantes = findViewById(R.id.botonDerechaTomosFaltantesComicDetail);
        scrollTomosFaltantes = findViewById(R.id.scrollTomosFaltantesComicDetail);
        contenedorTomosFaltantes = findViewById(R.id.contenedorTomosFaltantesComicDetail);
        textoEstadoTomosFaltantes = findViewById(R.id.textoEstadoTomosFaltantesComicDetail);
        barraCarga = findViewById(R.id.barraCargaComicDetail);
        textoError = findViewById(R.id.textoErrorComicDetail);
        panelFormularioResena = findViewById(R.id.panelFormularioResena);
        contenedorEstrellas = findViewById(R.id.contenedorEstrellas);
        campoComentarioResena = findViewById(R.id.campoComentarioResena);
        textoErrorResena = findViewById(R.id.textoErrorResena);
        botonGuardarResena = findViewById(R.id.botonGuardarResena);
        botonEliminarResena = findViewById(R.id.botonEliminarResena);
        textoSinPermisosResena = findViewById(R.id.textoSinPermisosResena);
        contenedorListaResenas = findViewById(R.id.contenedorListaResenas);
        botonCargarMasResenas = findViewById(R.id.botonCargarMasResenas);
        textoEstadoResenas = findViewById(R.id.textoEstadoResenas);
    }

    // Configura los botones de desplazamiento del carrusel faltante.
    private void setupMissingCarouselScroll() {
        botonIzquierdaTomosFaltantes.setOnClickListener(v -> scrollTomosFaltantes.smoothScrollBy(-420, 0));
        botonDerechaTomosFaltantes.setOnClickListener(v -> scrollTomosFaltantes.smoothScrollBy(420, 0));
    }

    // Conecta la accion de reportar comic.
    private void setupReportAction() {
        botonReportarComic.setOnClickListener(v -> openComicReportForm());
    }

    // Crea los 5 botones de estrellas para seleccionar calificacion.
    private void setupStarSelector() {
        contenedorEstrellas.removeAllViews();
        botonesEstrellas.clear();
        for (int i = 1; i <= 5; i++) {
            final int valorEstrella = i;
            TextView estrella = new TextView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dpToPx(40), dpToPx(40));
            params.setMarginEnd(dpToPx(4));
            estrella.setLayoutParams(params);
            estrella.setText(getString(R.string.resenas_estrella_vacia));
            estrella.setTextSize(26f);
            estrella.setGravity(android.view.Gravity.CENTER);
            estrella.setOnClickListener(v -> {
                calificacionSeleccionada = valorEstrella;
                updateStarDisplay();
            });
            botonesEstrellas.add(estrella);
            contenedorEstrellas.addView(estrella);
        }
        botonGuardarResena.setOnClickListener(v -> submitResena());
        botonEliminarResena.setOnClickListener(v -> confirmDeleteResena());
        botonCargarMasResenas.setOnClickListener(v -> loadMoreResenas());
    }

    // Actualiza el aspecto visual de las estrellas segun la calificacion seleccionada.
    private void updateStarDisplay() {
        for (int i = 0; i < botonesEstrellas.size(); i++) {
            boolean llena = (i + 1) <= calificacionSeleccionada;
            botonesEstrellas.get(i).setText(llena
                    ? getString(R.string.resenas_estrella_llena)
                    : getString(R.string.resenas_estrella_vacia));
        }
    }

    // Carga los detalles del comic desde Firestore.
    private void loadComicDetail() {
        barraCarga.setVisibility(View.VISIBLE);

        ComicDetailRepository.getComicDetail(comicId)
                .addOnSuccessListener(comic -> {
                    if (comic != null) {
                        renderComicDetail(comic);
                        loadVolumesList();
                        loadResenaSection();
                        loadResenas(false);
                    } else {
                        showError(getString(R.string.error_cargar_comic));
                        barraCarga.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(error -> {
                    showError(getString(R.string.error_cargar_comic));
                    barraCarga.setVisibility(View.GONE);
                });
    }

    // Abre el formulario reusable para reportar el comic actual.
    private void openComicReportForm() {
        if (TextUtils.isEmpty(uidUsuario) || TextUtils.isEmpty(comicId)) {
            showError(getString(R.string.reporte_error_envio));
            return;
        }

        reporteFormularioActual = new ReportFormComponent(
                this,
                getString(R.string.reporte_titulo_comic),
                getReportReasons(),
                getString(R.string.reporte_confirmacion_comic),
                (motivo, descripcion, capturaPantalla) -> ReportRepository.createContentReport(
                        uidUsuario,
                        comicId,
                        "comic",
                        "",
                        motivo,
                        descripcion,
                        capturaPantalla
                ),
                selectorCapturaReporte
        );
        reporteFormularioActual.show();
    }

    // Devuelve los motivos validos para reportes de contenido.
    private String[] getReportReasons() {
        return new String[]{
                getString(R.string.reporte_motivo_contenido_inapropiado),
                getString(R.string.reporte_motivo_informacion_incorrecta)
        };
    }

    // Renderiza la información del comic en la pantalla.
    private void renderComicDetail(ComicDetailData comic) {
        textoNombreComic.setText(comic.nombre != null ? comic.nombre : "");
        textoAutores.setText("Autor: " + comic.getAutoresFormateados());
        textoEditorial.setText("Editorial: " + String.valueOf(comic.editorial) +
                (comic.paisEditorial != null ? " (" + comic.paisEditorial + ")" : ""));
        textoEstado.setText("Estado: " + String.valueOf(comic.estado));
        textoFormato.setText("Formato: " + String.valueOf(comic.formato));
        textoGeneros.setText("Generos: " + comic.getGenerosFormateados());
        textoDescripcion.setText(comic.descripcion != null ? comic.descripcion : "");
        textoCalificacion.setText("Calificacion: " + comic.getCalificacionFormateada());
    }

    // Carga la seccion de resena del usuario (verifica biblioteca y resena previa).
    private void loadResenaSection() {
        if (TextUtils.isEmpty(uidUsuario)) {
            textoSinPermisosResena.setText(getString(R.string.resenas_sin_biblioteca));
            textoSinPermisosResena.setVisibility(View.VISIBLE);
            return;
        }

        ComicReviewRepository.hasVolumeInLibrary(uidUsuario, comicId)
                .addOnSuccessListener(tieneTomo -> {
                    if (!tieneTomo) {
                        textoSinPermisosResena.setText(getString(R.string.resenas_sin_biblioteca));
                        textoSinPermisosResena.setVisibility(View.VISIBLE);
                        panelFormularioResena.setVisibility(View.GONE);
                        return;
                    }
                    textoSinPermisosResena.setVisibility(View.GONE);
                    panelFormularioResena.setVisibility(View.VISIBLE);
                    loadUserResena();
                })
                .addOnFailureListener(error -> {
                    textoSinPermisosResena.setText(getString(R.string.resenas_sin_biblioteca));
                    textoSinPermisosResena.setVisibility(View.VISIBLE);
                });
    }

    // Carga la resena propia del usuario si ya existe.
    private void loadUserResena() {
        ComicReviewRepository.getUserReview(comicId, uidUsuario)
                .addOnSuccessListener(resena -> {
                    resenaUsuario = resena;
                    if (resena != null) {
                        calificacionSeleccionada = resena.calificacion;
                        updateStarDisplay();
                        campoComentarioResena.setText(resena.descripcion);
                        botonEliminarResena.setVisibility(View.VISIBLE);
                        botonGuardarResena.setText(getString(R.string.resenas_boton_guardar));
                    } else {
                        calificacionSeleccionada = 0;
                        updateStarDisplay();
                        campoComentarioResena.setText("");
                        botonEliminarResena.setVisibility(View.GONE);
                    }
                });
    }

    // Envia o actualiza la resena del usuario.
    private void submitResena() {
        textoErrorResena.setVisibility(View.GONE);
        if (calificacionSeleccionada < 1) {
            textoErrorResena.setText(getString(R.string.resenas_error_calificacion));
            textoErrorResena.setVisibility(View.VISIBLE);
            return;
        }

        String comentario = campoComentarioResena.getText() != null
                ? sanitize(campoComentarioResena.getText().toString().trim()) : "";

        botonGuardarResena.setEnabled(false);

        if (resenaUsuario != null) {
            ComicReviewRepository.updateReview(comicId, resenaUsuario.id, comentario, calificacionSeleccionada)
                    .addOnSuccessListener(v -> onResenaGuardada())
                    .addOnFailureListener(error -> onErrorResena(getString(R.string.resenas_error_guardar)));
        } else {
            ComicReviewRepository.addReview(comicId, uidUsuario, comentario, calificacionSeleccionada)
                    .addOnSuccessListener(v -> onResenaGuardada())
                    .addOnFailureListener(error -> onErrorResena(getString(R.string.resenas_error_guardar)));
        }
    }

    // Acciones tras guardar la resena exitosamente.
    private void onResenaGuardada() {
        botonGuardarResena.setEnabled(true);
        loadUserResena();
        refreshCalificacionComic();
        ultimoIdResena = null;
        loadResenas(false);
    }

    // Muestra el error en el formulario de resenas.
    private void onErrorResena(String mensaje) {
        botonGuardarResena.setEnabled(true);
        textoErrorResena.setText(mensaje);
        textoErrorResena.setVisibility(View.VISIBLE);
    }

    // Muestra confirmacion antes de eliminar la resena.
    private void confirmDeleteResena() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.resenas_confirmar_eliminar_titulo))
                .setMessage(getString(R.string.resenas_confirmar_eliminar_mensaje))
                .setPositiveButton(android.R.string.ok, (d, w) -> deleteResena())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // Elimina la resena del usuario.
    private void deleteResena() {
        if (resenaUsuario == null) return;
        botonEliminarResena.setEnabled(false);
        ComicReviewRepository.deleteReview(comicId, resenaUsuario.id)
                .addOnSuccessListener(v -> {
                    botonEliminarResena.setEnabled(true);
                    resenaUsuario = null;
                    calificacionSeleccionada = 0;
                    updateStarDisplay();
                    campoComentarioResena.setText("");
                    botonEliminarResena.setVisibility(View.GONE);
                    refreshCalificacionComic();
                    ultimoIdResena = null;
                    loadResenas(false);
                })
                .addOnFailureListener(error -> {
                    botonEliminarResena.setEnabled(true);
                    textoErrorResena.setText(getString(R.string.resenas_error_eliminar));
                    textoErrorResena.setVisibility(View.VISIBLE);
                });
    }

    // Refresca el texto de calificacion del comic tras guardar o eliminar.
    private void refreshCalificacionComic() {
        ComicDetailRepository.getComicDetail(comicId)
                .addOnSuccessListener(comic -> {
                    if (comic != null) textoCalificacion.setText("Calificacion: " + comic.getCalificacionFormateada());
                });
    }

    // Carga la primera pagina de resenas o reemplaza si es recarga.
    private void loadResenas(boolean acumulando) {
        textoEstadoResenas.setVisibility(View.GONE);
        botonCargarMasResenas.setVisibility(View.GONE);

        String cursor = acumulando ? ultimoIdResena : null;

        ComicReviewRepository.getComicReviews(comicId, 10, cursor)
                .addOnSuccessListener(pagina -> {
                    if (!acumulando) contenedorListaResenas.removeAllViews();
                    renderResenas(pagina);
                })
                .addOnFailureListener(error -> {
                    textoEstadoResenas.setText(getString(R.string.resenas_error_carga));
                    textoEstadoResenas.setVisibility(View.VISIBLE);
                });
    }

    // Carga 10 resenas mas usando el cursor de paginacion.
    private void loadMoreResenas() {
        loadResenas(true);
    }

    // Dibuja las resenas recibidas en la pantalla.
    private void renderResenas(ComicReviewPageData pagina) {
        if (pagina.resenas.isEmpty() && contenedorListaResenas.getChildCount() == 0) {
            textoEstadoResenas.setText(getString(R.string.resenas_sin_resenas));
            textoEstadoResenas.setVisibility(View.VISIBLE);
            botonCargarMasResenas.setVisibility(View.GONE);
            return;
        }

        for (ComicReviewData resena : pagina.resenas) {
            contenedorListaResenas.addView(buildResenaCard(resena));
        }

        ultimoIdResena = pagina.ultimoId;
        hayMasResenas = pagina.hayMas;

        if (hayMasResenas) {
            botonCargarMasResenas.setVisibility(View.VISIBLE);
            textoEstadoResenas.setVisibility(View.GONE);
        } else {
            botonCargarMasResenas.setVisibility(View.GONE);
            if (contenedorListaResenas.getChildCount() > 0) {
                textoEstadoResenas.setText(getString(R.string.resenas_sin_mas));
                textoEstadoResenas.setVisibility(View.VISIBLE);
            }
        }
    }

    // Crea la tarjeta visual de una resena con foto, nick, estrellas y comentario.
    private View buildResenaCard(ComicReviewData resena) {
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dpToPx(12);
        card.setLayoutParams(params);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        card.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);

        // Fila superior: foto + nick.
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

        TextView textoNick = new TextView(this);
        textoNick.setText(resena.usuarioId);
        textoNick.setTextSize(13f);
        textoNick.setTypeface(null, android.graphics.Typeface.BOLD);
        textoNick.setTextColor(getResources().getColor(android.R.color.holo_blue_dark, getTheme()));
        textoNick.setOnClickListener(v -> openUserProfile(resena.usuarioId));
        columnaAutor.addView(textoNick);

        // Estrellas de la calificacion (solo lectura).
        TextView textoEstrellas = new TextView(this);
        textoEstrellas.setText(buildStarsDisplay(resena.calificacion));
        textoEstrellas.setTextSize(16f);
        columnaAutor.addView(textoEstrellas);

        if (resena.fecha != null) {
            TextView textoFecha = new TextView(this);
            textoFecha.setText(formatDate(resena.fecha));
            textoFecha.setTextSize(11f);
            textoFecha.setTextColor(getResources().getColor(android.R.color.darker_gray, getTheme()));
            columnaAutor.addView(textoFecha);
        }

        filaAutor.addView(columnaAutor);
        card.addView(filaAutor);

        // Comentario opcional.
        if (!TextUtils.isEmpty(resena.descripcion)) {
            TextView textoComentario = new TextView(this);
            LinearLayout.LayoutParams cParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cParams.topMargin = dpToPx(6);
            textoComentario.setLayoutParams(cParams);
            textoComentario.setText(resena.descripcion);
            textoComentario.setTextSize(14f);
            card.addView(textoComentario);
        }

        // Carga nick y foto del autor de manera asincrona.
        if (!TextUtils.isEmpty(resena.usuarioId)) {
            loadAuthorData(resena.usuarioId, fotoPerfil, textoNick);
        }

        return card;
    }

    // Carga nick y foto de perfil de forma asincrona.
    private void loadAuthorData(String uid, ImageView fotoView, TextView nickView) {
        FirebaseFirestore.getInstance()
                .collection("usuario")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) return;
                    String nick = doc.getString("Nick");
                    if (!TextUtils.isEmpty(nick)) nickView.setText(nick);
                    Object fotoPerfil = doc.get("FotoPerfil");
                    if (fotoPerfil instanceof java.util.Map) {
                        Object dataUrl = ((java.util.Map<?, ?>) fotoPerfil).get("dataUrl");
                        if (dataUrl != null) {
                            Bitmap bitmap = decodeDataUrl(String.valueOf(dataUrl));
                            if (bitmap != null) fotoView.setImageBitmap(bitmap);
                        }
                    }
                });
    }

    // Construye la cadena de estrellas llenas y vacias segun calificacion.
    private String buildStarsDisplay(int calificacion) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            sb.append(i <= calificacion
                    ? getString(R.string.resenas_estrella_llena)
                    : getString(R.string.resenas_estrella_vacia));
        }
        return sb.toString();
    }

    // Abre el perfil de un usuario por su UID.
    private void openUserProfile(String uid) {
        if (TextUtils.isEmpty(uid)) return;
        FriendshipRepository.canOpenUserProfile(uidUsuario, uid)
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

    // Carga la lista de tomos del comic.
    private void loadVolumesList() {
        ComicDetailRepository.getComicVolumes(comicId)
                .addOnSuccessListener(tomos -> {
                    barraCarga.setVisibility(View.GONE);
                    if (tomos != null && !tomos.isEmpty()) {
                        tomosDelComic.clear();
                        tomosDelComic.addAll(tomos);
                        setupGridAdapter();
                    } else {
                        tomosDelComic.clear();
                        setupGridAdapter();
                    }
                    loadMissingVolumesForComic();
                })
                .addOnFailureListener(error -> {
                    barraCarga.setVisibility(View.GONE);
                    loadMissingVolumesForComic();
                });
    }

    // Configura el adaptador para mostrar portadas de tomos.
    private void setupGridAdapter() {
        adaptadorPortadas = new VolumePortadaAdapter(this, tomosDelComic, this::openVolumeDetail);
        gridPortadasTomos.setAdapter(adaptadorPortadas);
    }

    // Carga y muestra los tomos faltantes del comic para el usuario actual.
    private void loadMissingVolumesForComic() {
        if (TextUtils.isEmpty(uidUsuario) || TextUtils.isEmpty(comicId)) {
            showMissingVolumesState(getString(R.string.comic_detail_tomos_faltantes_sin_biblioteca), false);
            return;
        }

        UserShelfRepository.getComicMissingVolumes(uidUsuario, comicId)
                .addOnSuccessListener(this::renderMissingVolumes)
                .addOnFailureListener(error ->
                        showMissingVolumesState(getString(R.string.comic_detail_tomos_faltantes_error), false));
    }

    // Renderiza el estado del carrusel de tomos faltantes.
    private void renderMissingVolumes(ComicMissingVolumesData datos) {
        if (datos == null || !datos.tieneComicEnBiblioteca) {
            showMissingVolumesState(getString(R.string.comic_detail_tomos_faltantes_sin_biblioteca), false);
            return;
        }

        List<MissingVolumeData> faltantes = datos.tomosFaltantes != null ? datos.tomosFaltantes : new ArrayList<>();
        if (faltantes.isEmpty()) {
            showMissingVolumesState(getString(R.string.tomos_faltantes_coleccion_al_dia), false);
            return;
        }

        textoTituloTomosFaltantes.setVisibility(View.VISIBLE);
        contenedorCarruselTomosFaltantes.setVisibility(View.VISIBLE);
        textoEstadoTomosFaltantes.setVisibility(View.GONE);
        componenteCarruselFaltantes.renderVolumes(
                contenedorTomosFaltantes, faltantes, false, this::openMissingVolumeDetail);
    }

    // Muestra un mensaje de estado para tomos faltantes.
    private void showMissingVolumesState(String mensaje, boolean mostrarCarrusel) {
        textoTituloTomosFaltantes.setVisibility(View.VISIBLE);
        contenedorCarruselTomosFaltantes.setVisibility(mostrarCarrusel ? View.VISIBLE : View.GONE);
        textoEstadoTomosFaltantes.setText(mensaje);
        textoEstadoTomosFaltantes.setVisibility(View.VISIBLE);
        if (!mostrarCarrusel) contenedorTomosFaltantes.removeAllViews();
    }

    // Abre detalle desde una tarjeta de tomo faltante.
    private void openMissingVolumeDetail(MissingVolumeData tomoFaltante) {
        if (tomoFaltante == null || tomoFaltante.tomo == null) return;
        Intent pantallaDetalleTomo = new Intent(this, VolumeDetailActivity.class);
        pantallaDetalleTomo.putExtra(VolumeDetailActivity.EXTRA_COMIC_ID, tomoFaltante.comicId);
        pantallaDetalleTomo.putExtra(VolumeDetailActivity.EXTRA_VOLUME_ID, tomoFaltante.tomo.id);
        startActivity(pantallaDetalleTomo);
    }

    // Abre la pantalla de detalles del tomo.
    private void openVolumeDetail(VolumeDetailData tomo) {
        Intent pantallaDetalleTomo = new Intent(this, VolumeDetailActivity.class);
        pantallaDetalleTomo.putExtra(VolumeDetailActivity.EXTRA_COMIC_ID, comicId);
        pantallaDetalleTomo.putExtra(VolumeDetailActivity.EXTRA_VOLUME_ID, tomo.id);
        startActivity(pantallaDetalleTomo);
    }

    // Formatea fecha para mostrar en tarjeta de resena.
    private String formatDate(java.util.Date fecha) {
        try {
            return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(fecha);
        } catch (Exception ignored) {
            return "";
        }
    }

    // Remueve los caracteres prohibidos del texto ingresado.
    private String sanitize(String texto) {
        if (texto == null) return "";
        return texto.replaceAll("[@#$^&*{}\\[\\]<>]", "");
    }

    // Convierte dataUrl a bitmap para fotos de perfil.
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

    // Muestra un mensaje de error.
    private void showError(String mensaje) {
        textoError.setText(mensaje);
        textoError.setVisibility(View.VISIBLE);
    }
}
