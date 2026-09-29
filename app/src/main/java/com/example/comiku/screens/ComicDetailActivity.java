package com.example.comiku.screens;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.example.comiku.core.ui.StatusBarUtils;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.example.comiku.R;
import com.example.comiku.core.ui.DeleteConfirmDialogComponent;
import com.example.comiku.core.ui.ToastUtils;
import com.example.comiku.core.ui.MissingVolumeCarouselComponent;
import com.example.comiku.core.ui.RecentVolumeCarouselComponent;
import com.example.comiku.core.ui.ReportFormComponent;
import com.example.comiku.core.ui.ThematicListUiHelper;
import com.example.comiku.data.model.ComicDetailData;
import com.example.comiku.data.model.ComicReviewData;
import com.example.comiku.data.model.ComicReviewPageData;
import com.example.comiku.data.model.MissingVolumeData;
import com.example.comiku.data.model.RecentLibraryVolumeData;
import com.example.comiku.data.model.UserShelfVolumeItemData;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ComicDetailActivity extends BasePlainScreenActivity {
    public static final String EXTRA_COMIC_ID = "extra_comic_id";

    private TextView textoNombreComic;
    private TextView textoAutores;
    private TextView textoEditorial;
    private TextView textoEstado;
    private TextView textoFormato;
    private TextView textoGeneros;
    private TextView textoDescripcion;
    private TextView textoPromedio;
    private ReviewStarView iconoPromedio;
    private Button botonReportarComic;
    private LinearLayout seccionCarruselTomos;
    private ImageButton botonIzquierdaTomos;
    private ImageButton botonDerechaTomos;
    private HorizontalScrollView scrollTomos;
    private LinearLayout contenedorCarruselTomos;
    private LinearLayout contenedorTomos;
    private TextView textoEstadoTomos;
    private LinearLayout seccionCarruselTomosFaltantes;
    private LinearLayout contenedorCarruselTomosFaltantes;
    private ImageButton botonIzquierdaTomosFaltantes;
    private ImageButton botonDerechaTomosFaltantes;
    private HorizontalScrollView scrollTomosFaltantes;
    private LinearLayout contenedorTomosFaltantes;
    private TextView textoEstadoTomosFaltantes;
    private LinearLayout seccionCarruselTomosBiblioteca;
    private ImageButton botonIzquierdaTomosBiblioteca;
    private ImageButton botonDerechaTomosBiblioteca;
    private HorizontalScrollView scrollTomosBiblioteca;
    private LinearLayout contenedorCarruselTomosBiblioteca;
    private LinearLayout contenedorTomosBiblioteca;
    private TextView textoEstadoTomosBiblioteca;
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
    private LinearLayout contenedorMiResena;
    private View divisorMiResena;
    private LinearLayout contenedorListaResenas;
    private Button botonCargarMasResenas;
    private TextView textoEstadoResenas;

    private String comicId;
    private String uidUsuario;
    private ComicDetailData comicActual;
    private List<VolumeDetailData> tomosDelComic = new ArrayList<>();
    private MissingVolumeCarouselComponent componenteCarruselFaltantes;
    private RecentVolumeCarouselComponent componenteCarruselTomos;
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
    private final List<ReviewStarView> botonesEstrellas = new ArrayList<>();

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
        setupPlainScreenShell(R.layout.activity_comic_detail);
        bindViews();
        componenteCarruselTomos = new RecentVolumeCarouselComponent(this);
        componenteCarruselFaltantes = new MissingVolumeCarouselComponent(this);
        setupVolumeCarouselScroll();
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
        textoPromedio = findViewById(R.id.textoPromedioComicDetail);
        iconoPromedio = findViewById(R.id.iconoPromedioComicDetail);
        botonReportarComic = findViewById(R.id.botonReportarComicDetail);
        seccionCarruselTomos = findViewById(R.id.seccionCarruselTomosComicDetail);
        botonIzquierdaTomos = findViewById(R.id.botonIzquierdaTomosComicDetail);
        botonDerechaTomos = findViewById(R.id.botonDerechaTomosComicDetail);
        scrollTomos = findViewById(R.id.scrollTomosComicDetail);
        contenedorCarruselTomos = findViewById(R.id.contenedorCarruselTomosComicDetail);
        contenedorTomos = findViewById(R.id.contenedorTomosComicDetail);
        textoEstadoTomos = findViewById(R.id.textoEstadoTomosComicDetail);
        seccionCarruselTomosFaltantes = findViewById(R.id.seccionCarruselTomosFaltantesComicDetail);
        contenedorCarruselTomosFaltantes = findViewById(R.id.contenedorCarruselTomosFaltantesComicDetail);
        botonIzquierdaTomosFaltantes = findViewById(R.id.botonIzquierdaTomosFaltantesComicDetail);
        botonDerechaTomosFaltantes = findViewById(R.id.botonDerechaTomosFaltantesComicDetail);
        scrollTomosFaltantes = findViewById(R.id.scrollTomosFaltantesComicDetail);
        contenedorTomosFaltantes = findViewById(R.id.contenedorTomosFaltantesComicDetail);
        textoEstadoTomosFaltantes = findViewById(R.id.textoEstadoTomosFaltantesComicDetail);
        seccionCarruselTomosBiblioteca = findViewById(R.id.seccionCarruselTomosBibliotecaComicDetail);
        botonIzquierdaTomosBiblioteca = findViewById(R.id.botonIzquierdaTomosBibliotecaComicDetail);
        botonDerechaTomosBiblioteca = findViewById(R.id.botonDerechaTomosBibliotecaComicDetail);
        scrollTomosBiblioteca = findViewById(R.id.scrollTomosBibliotecaComicDetail);
        contenedorCarruselTomosBiblioteca = findViewById(R.id.contenedorCarruselTomosBibliotecaComicDetail);
        contenedorTomosBiblioteca = findViewById(R.id.contenedorTomosBibliotecaComicDetail);
        textoEstadoTomosBiblioteca = findViewById(R.id.textoEstadoTomosBibliotecaComicDetail);
        barraCarga = findViewById(R.id.barraCargaComicDetail);
        textoError = findViewById(R.id.textoErrorComicDetail);
        panelFormularioResena = findViewById(R.id.panelFormularioResena);
        contenedorEstrellas = findViewById(R.id.contenedorEstrellas);
        campoComentarioResena = findViewById(R.id.campoComentarioResena);
        textoErrorResena = findViewById(R.id.textoErrorResena);
        botonGuardarResena = findViewById(R.id.botonGuardarResena);
        botonEliminarResena = findViewById(R.id.botonEliminarResena);
        textoSinPermisosResena = findViewById(R.id.textoSinPermisosResena);
        contenedorMiResena = findViewById(R.id.contenedorMiResenaComicDetail);
        divisorMiResena = findViewById(R.id.divisorMiResenaComicDetail);
        contenedorListaResenas = findViewById(R.id.contenedorListaResenas);
        botonCargarMasResenas = findViewById(R.id.botonCargarMasResenas);
        textoEstadoResenas = findViewById(R.id.textoEstadoResenas);
    }

    // Configura el desplazamiento de los carruseles de tomos.
    private void setupVolumeCarouselScroll() {
        botonIzquierdaTomos.setOnClickListener(v -> scrollTomos.smoothScrollBy(-420, 0));
        botonDerechaTomos.setOnClickListener(v -> scrollTomos.smoothScrollBy(420, 0));
        botonIzquierdaTomosFaltantes.setOnClickListener(v -> scrollTomosFaltantes.smoothScrollBy(-420, 0));
        botonDerechaTomosFaltantes.setOnClickListener(v -> scrollTomosFaltantes.smoothScrollBy(420, 0));
        botonIzquierdaTomosBiblioteca.setOnClickListener(v -> scrollTomosBiblioteca.smoothScrollBy(-420, 0));
        botonDerechaTomosBiblioteca.setOnClickListener(v -> scrollTomosBiblioteca.smoothScrollBy(420, 0));
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
            ReviewStarView estrella = new ReviewStarView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dpToPx(40), dpToPx(40));
            params.setMarginEnd(dpToPx(4));
            estrella.setLayoutParams(params);
            estrella.setTextSize(26f);
            estrella.setGravity(android.view.Gravity.CENTER);
            estrella.setFilled(false);
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
            botonesEstrellas.get(i).setFilled(llena);
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
        comicActual = comic;
        textoNombreComic.setText(comic.nombre != null ? comic.nombre : "");
        textoAutores.setText(buildMetadataText("Autor", comic.getAutoresFormateados()));
        textoEditorial.setText(buildMetadataText(
                "Editorial",
                String.valueOf(comic.editorial) + (comic.paisEditorial != null ? " (" + comic.paisEditorial + ")" : "")
        ));
        textoEstado.setText(buildMetadataText("Estado", String.valueOf(comic.estado)));
        textoFormato.setText(buildMetadataText("Formato", String.valueOf(comic.formato)));
        textoGeneros.setText(buildMetadataText("Generos", comic.getGenerosFormateados()));
        textoDescripcion.setText(comic.descripcion != null ? comic.descripcion : "");
        updateAverageDisplay(comic.getCalificacionFormateada());
    }

    // Arma el texto de metadata con label en negrita y valor normal.
    private CharSequence buildMetadataText(String label, String valor) {
        SpannableStringBuilder texto = new SpannableStringBuilder();
        String prefijo = label + ": ";
        texto.append(prefijo);
        texto.setSpan(new StyleSpan(Typeface.BOLD), 0, prefijo.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        texto.append(valor);
        return texto;
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
                        renderMyReviewCard(null);
                        return;
                    }
                    textoSinPermisosResena.setVisibility(View.GONE);
                    panelFormularioResena.setVisibility(View.VISIBLE);
                    loadUserResena();
                })
                .addOnFailureListener(error -> {
                    textoSinPermisosResena.setText(getString(R.string.resenas_sin_biblioteca));
                    textoSinPermisosResena.setVisibility(View.VISIBLE);
                    renderMyReviewCard(null);
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
                        botonGuardarResena.setText(getString(R.string.resenas_boton_actualizar));
                        renderMyReviewCard(resena);
                    } else {
                        calificacionSeleccionada = 0;
                        updateStarDisplay();
                        campoComentarioResena.setText("");
                        botonEliminarResena.setVisibility(View.GONE);
                        botonGuardarResena.setText(getString(R.string.resenas_boton_publicar));
                        renderMyReviewCard(null);
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
        DeleteConfirmDialogComponent dialogoConfirmacion = new DeleteConfirmDialogComponent(
                this,
                getString(R.string.resenas_confirmar_eliminar_titulo),
                getString(R.string.resenas_confirmar_eliminar_mensaje),
                this::deleteResena
        );
        dialogoConfirmacion.show();
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
                    botonGuardarResena.setText(getString(R.string.resenas_boton_publicar));
                    renderMyReviewCard(null);
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
                    if (comic != null) {
                        updateAverageDisplay(comic.getCalificacionFormateada());
                    }
                });
    }

    // Actualiza el promedio visible dentro de la seccion de reseñas.
    private void updateAverageDisplay(String promedio) {
        if (iconoPromedio != null) {
            iconoPromedio.setFilled(true);
        }
        if (textoPromedio != null) {
            textoPromedio.setText(getString(
                    R.string.resenas_promedio,
                    String.valueOf(promedio == null ? "" : promedio)
            ));
        }
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
        List<ComicReviewData> resenasFiltradas = filterOutCurrentUserReview(
                pagina != null ? pagina.resenas : null
        );
        if (resenasFiltradas.isEmpty() && contenedorListaResenas.getChildCount() == 0) {
            textoEstadoResenas.setText(getString(R.string.resenas_sin_resenas));
            textoEstadoResenas.setVisibility(View.VISIBLE);
            botonCargarMasResenas.setVisibility(View.GONE);
            return;
        }

        for (ComicReviewData resena : resenasFiltradas) {
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
        card.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));
        card.setBackgroundResource(R.drawable.bg_card_generic_premium);

        // Fila superior: foto + nick.
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

        TextView textoNick = new TextView(this);
        textoNick.setText(resena.usuarioId);
        textoNick.setTextSize(15f);
        textoNick.setTypeface(null, android.graphics.Typeface.BOLD);
        textoNick.setTextColor(getColor(R.color.primary_button_orange_flat));
        textoNick.setOnClickListener(v -> openUserProfile(resena.usuarioId));
        columnaAutor.addView(textoNick);

        // Estrellas de la calificacion (solo lectura).
        columnaAutor.addView(buildStarsRow(resena.calificacion, 18, 3));

        if (resena.fecha != null) {
            TextView textoFecha = new TextView(this);
            textoFecha.setText(formatDate(resena.fecha));
            textoFecha.setTextSize(12f);
            textoFecha.setTextColor(getColor(R.color.carousel_subtitle));
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
            textoComentario.setPadding(dpToPx(2), dpToPx(6), dpToPx(2), dpToPx(2));
            textoComentario.setTextSize(16f);
            textoComentario.setTextColor(getColor(R.color.carousel_subtitle));
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

    // Crea una fila de estrellas para mostrar una calificacion.
    private LinearLayout buildStarsRow(int calificacion, int tamanoSp, int margenFinDp) {
        LinearLayout filaEstrellas = new LinearLayout(this);
        filaEstrellas.setOrientation(LinearLayout.HORIZONTAL);

        for (int i = 1; i <= 5; i++) {
            ReviewStarView estrella = new ReviewStarView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (i < 5) {
                params.setMarginEnd(dpToPx(margenFinDp));
            }
            estrella.setLayoutParams(params);
            estrella.setTextSize(tamanoSp);
            estrella.setFilled(i <= calificacion);
            filaEstrellas.addView(estrella);
        }

        return filaEstrellas;
    }

    // Muestra la reseña propia debajo del formulario para separarla del resto.
    private void renderMyReviewCard(ComicReviewData resena) {
        if (contenedorMiResena == null || divisorMiResena == null) {
            return;
        }

        contenedorMiResena.removeAllViews();
        if (resena == null) {
            contenedorMiResena.setVisibility(View.GONE);
            divisorMiResena.setVisibility(View.GONE);
            return;
        }

        contenedorMiResena.setVisibility(View.VISIBLE);
        divisorMiResena.setVisibility(View.VISIBLE);
        contenedorMiResena.addView(buildResenaCard(resena));
    }

    // Quita la reseña propia de la lista general para no duplicarla.
    private List<ComicReviewData> filterOutCurrentUserReview(List<ComicReviewData> resenas) {
        List<ComicReviewData> resenasFiltradas = new ArrayList<>();
        if (resenas == null) {
            return resenasFiltradas;
        }

        for (ComicReviewData resena : resenas) {
            if (resena == null) {
                continue;
            }
            if (!TextUtils.isEmpty(uidUsuario) && uidUsuario.equals(resena.usuarioId)) {
                continue;
            }
            resenasFiltradas.add(resena);
        }
        return resenasFiltradas;
    }

    // Abre el perfil de un usuario por su UID.
    private void openUserProfile(String uid) {
        if (TextUtils.isEmpty(uid)) return;
        FriendshipRepository.canOpenUserProfile(uidUsuario, uid)
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

    // Carga la lista de tomos del comic.
    private void loadVolumesList() {
        ComicDetailRepository.getComicVolumes(comicId)
                .addOnSuccessListener(tomos -> {
                    barraCarga.setVisibility(View.GONE);
                    tomosDelComic.clear();
                    tomosDelComic.addAll(sortVolumesByNumber(tomos));
                    loadVolumeCarousels();
                })
                .addOnFailureListener(error -> {
                    barraCarga.setVisibility(View.GONE);
                    tomosDelComic.clear();
                    showAllVolumesState(getString(R.string.error_cargar_comic), false);
                    hideLibraryVolumeSections();
                });
    }

    // Carga el estado de biblioteca del comic para elegir carruseles.
    private void loadVolumeCarousels() {
        if (TextUtils.isEmpty(uidUsuario) || TextUtils.isEmpty(comicId)) {
            renderAllVolumesCarousel(tomosDelComic);
            return;
        }

        UserShelfRepository.getComicLibraryVolumes(uidUsuario, comicId)
                .addOnSuccessListener(this::renderVolumeSections)
                .addOnFailureListener(error -> {
                    renderAllVolumesCarousel(tomosDelComic);
                    hideLibraryVolumeSections();
                });
    }

    // Decide que carruseles mostrar segun la biblioteca del usuario.
    private void renderVolumeSections(List<UserShelfVolumeItemData> tomosBiblioteca) {
        List<UserShelfVolumeItemData> tomosGuardados = tomosBiblioteca != null
                ? new ArrayList<>(tomosBiblioteca)
                : new ArrayList<>();
        if (tomosGuardados.isEmpty()) {
            renderAllVolumesCarousel(tomosDelComic);
            hideLibraryVolumeSections();
            return;
        }

        seccionCarruselTomos.setVisibility(View.GONE);
        renderMissingVolumesCarousel(buildMissingVolumeItems(tomosGuardados));
        renderLibraryVolumesCarousel(tomosGuardados);
    }

    // Muestra todos los tomos del comic cuando aun no hay biblioteca.
    private void renderAllVolumesCarousel(List<VolumeDetailData> tomos) {
        seccionCarruselTomos.setVisibility(View.VISIBLE);
        if (tomos == null || tomos.isEmpty()) {
            showAllVolumesState(getString(R.string.comic_detail_tomos_todos_vacio), false);
            return;
        }

        contenedorCarruselTomos.setVisibility(View.VISIBLE);
        textoEstadoTomos.setVisibility(View.GONE);
        componenteCarruselTomos.renderVolumes(
                contenedorTomos,
                buildRecentVolumeItems(tomos),
                this::openRecentVolumeDetail
        );
    }

    // Muestra los tomos faltantes ordenados por numero.
    private void renderMissingVolumesCarousel(List<MissingVolumeData> faltantes) {
        seccionCarruselTomosFaltantes.setVisibility(View.VISIBLE);
        if (faltantes == null || faltantes.isEmpty()) {
            showMissingVolumesState(getString(R.string.tomos_faltantes_coleccion_al_dia), false);
            return;
        }

        contenedorCarruselTomosFaltantes.setVisibility(View.VISIBLE);
        textoEstadoTomosFaltantes.setVisibility(View.GONE);
        componenteCarruselFaltantes.renderVolumes(
                contenedorTomosFaltantes, faltantes, false, this::openMissingVolumeDetail);
    }

    // Muestra los tomos guardados del comic en la biblioteca.
    private void renderLibraryVolumesCarousel(List<UserShelfVolumeItemData> tomosBiblioteca) {
        seccionCarruselTomosBiblioteca.setVisibility(View.VISIBLE);
        if (tomosBiblioteca == null || tomosBiblioteca.isEmpty()) {
            showLibraryVolumesState(getString(R.string.comic_detail_tomos_biblioteca_vacio), false);
            return;
        }

        List<VolumeDetailData> tomosOrdenados = new ArrayList<>();
        for (UserShelfVolumeItemData tomoBiblioteca : tomosBiblioteca) {
            if (tomoBiblioteca != null && tomoBiblioteca.tomo != null) {
                tomosOrdenados.add(tomoBiblioteca.tomo);
            }
        }

        if (tomosOrdenados.isEmpty()) {
            showLibraryVolumesState(getString(R.string.comic_detail_tomos_biblioteca_vacio), false);
            return;
        }

        contenedorCarruselTomosBiblioteca.setVisibility(View.VISIBLE);
        textoEstadoTomosBiblioteca.setVisibility(View.GONE);
        componenteCarruselTomos.renderVolumes(
                contenedorTomosBiblioteca,
                buildRecentVolumeItems(tomosOrdenados),
                this::openRecentVolumeDetail
        );
    }

    // Muestra un mensaje en el carrusel general de tomos.
    private void showAllVolumesState(String mensaje, boolean mostrarCarrusel) {
        seccionCarruselTomos.setVisibility(View.VISIBLE);
        contenedorCarruselTomos.setVisibility(mostrarCarrusel ? View.VISIBLE : View.GONE);
        textoEstadoTomos.setText(mensaje);
        textoEstadoTomos.setVisibility(View.VISIBLE);
        if (!mostrarCarrusel) {
            contenedorTomos.removeAllViews();
        }
    }

    // Muestra un mensaje de estado para tomos faltantes.
    private void showMissingVolumesState(String mensaje, boolean mostrarCarrusel) {
        seccionCarruselTomosFaltantes.setVisibility(View.VISIBLE);
        contenedorCarruselTomosFaltantes.setVisibility(mostrarCarrusel ? View.VISIBLE : View.GONE);
        textoEstadoTomosFaltantes.setText(mensaje);
        textoEstadoTomosFaltantes.setVisibility(View.VISIBLE);
        if (!mostrarCarrusel) {
            contenedorTomosFaltantes.removeAllViews();
        }
    }

    // Muestra un mensaje en el carrusel de biblioteca.
    private void showLibraryVolumesState(String mensaje, boolean mostrarCarrusel) {
        seccionCarruselTomosBiblioteca.setVisibility(View.VISIBLE);
        contenedorCarruselTomosBiblioteca.setVisibility(mostrarCarrusel ? View.VISIBLE : View.GONE);
        textoEstadoTomosBiblioteca.setText(mensaje);
        textoEstadoTomosBiblioteca.setVisibility(View.VISIBLE);
        if (!mostrarCarrusel) {
            contenedorTomosBiblioteca.removeAllViews();
        }
    }

    // Oculta los carruseles ligados a la biblioteca.
    private void hideLibraryVolumeSections() {
        seccionCarruselTomosFaltantes.setVisibility(View.GONE);
        seccionCarruselTomosBiblioteca.setVisibility(View.GONE);
        contenedorTomosFaltantes.removeAllViews();
        contenedorTomosBiblioteca.removeAllViews();
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

    // Abre detalle desde una tarjeta de tomo reciente o de biblioteca.
    private void openRecentVolumeDetail(RecentLibraryVolumeData tomoReciente) {
        if (tomoReciente == null || tomoReciente.tomo == null) {
            return;
        }
        openVolumeDetail(tomoReciente.tomo);
    }

    // Arma los tomos faltantes de este comic con orden seguro.
    private List<MissingVolumeData> buildMissingVolumeItems(List<UserShelfVolumeItemData> tomosBiblioteca) {
        Set<String> idsBiblioteca = buildLibraryVolumeIds(tomosBiblioteca);
        List<MissingVolumeData> faltantes = new ArrayList<>();
        for (VolumeDetailData tomo : tomosDelComic) {
            if (tomo != null && !idsBiblioteca.contains(tomo.id)) {
                faltantes.add(new MissingVolumeData(comicId, getComicName(), tomo));
            }
        }
        faltantes.sort((tomoA, tomoB) -> compareVolumeOrder(
                tomoA != null ? tomoA.tomo : null,
                tomoB != null ? tomoB.tomo : null
        ));
        return faltantes;
    }

    // Convierte tomos en tarjetas con el estilo del inicio.
    private List<RecentLibraryVolumeData> buildRecentVolumeItems(List<VolumeDetailData> tomos) {
        List<RecentLibraryVolumeData> tomosCarrusel = new ArrayList<>();
        if (tomos == null) {
            return tomosCarrusel;
        }

        for (VolumeDetailData tomo : tomos) {
            if (tomo != null) {
                tomosCarrusel.add(new RecentLibraryVolumeData(
                        comicId,
                        getComicName(),
                        tomo,
                        null
                ));
            }
        }
        return tomosCarrusel;
    }

    // Junta los ids de los tomos guardados en biblioteca.
    private Set<String> buildLibraryVolumeIds(List<UserShelfVolumeItemData> tomosBiblioteca) {
        Set<String> idsBiblioteca = new HashSet<>();
        if (tomosBiblioteca == null) {
            return idsBiblioteca;
        }

        for (UserShelfVolumeItemData tomoBiblioteca : tomosBiblioteca) {
            if (tomoBiblioteca != null && tomoBiblioteca.tomo != null && !TextUtils.isEmpty(tomoBiblioteca.tomo.id)) {
                idsBiblioteca.add(tomoBiblioteca.tomo.id);
            }
        }
        return idsBiblioteca;
    }

    // Ordena los tomos por numero sin asumir continuidad.
    private List<VolumeDetailData> sortVolumesByNumber(List<VolumeDetailData> tomos) {
        List<VolumeDetailData> tomosOrdenados = tomos != null ? new ArrayList<>(tomos) : new ArrayList<>();
        tomosOrdenados.sort(this::compareVolumeOrder);
        return tomosOrdenados;
    }

    // Compara dos tomos para mostrarlos del menor al mayor.
    private int compareVolumeOrder(VolumeDetailData tomoA, VolumeDetailData tomoB) {
        int comparacionOrden = Integer.compare(getVolumeOrderValue(tomoA), getVolumeOrderValue(tomoB));
        if (comparacionOrden != 0) {
            return comparacionOrden;
        }

        String idA = tomoA != null && tomoA.id != null ? tomoA.id : "";
        String idB = tomoB != null && tomoB.id != null ? tomoB.id : "";
        return idA.compareTo(idB);
    }

    // Devuelve el valor de orden del tomo.
    private int getVolumeOrderValue(VolumeDetailData tomo) {
        if (tomo == null) {
            return Integer.MAX_VALUE;
        }
        if (tomo.numeroTomo != null) {
            return tomo.numeroTomo;
        }
        if (Boolean.TRUE.equals(tomo.tomoUnico)) {
            return Integer.MIN_VALUE;
        }
        return Integer.MAX_VALUE - 1;
    }

    // Devuelve el nombre actual del comic para las tarjetas.
    private String getComicName() {
        return comicActual != null && comicActual.nombre != null ? comicActual.nombre : "";
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
