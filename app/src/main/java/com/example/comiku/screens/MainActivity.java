package com.example.comiku.screens;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.comiku.R;
import com.example.comiku.core.ui.HomePresentationCarouselComponent;
import com.example.comiku.core.ui.ToastUtils;
import com.example.comiku.core.ui.MissingVolumeCarouselComponent;
import com.example.comiku.core.ui.RecentVolumeCarouselComponent;
import com.example.comiku.core.ui.RecommendationCarouselComponent;
import com.example.comiku.data.model.ComicRecommendationData;
import com.example.comiku.data.model.HomeMissingVolumesData;
import com.example.comiku.data.model.HomeRecentVolumesData;
import com.example.comiku.data.model.HomeRecommendationsData;
import com.example.comiku.data.model.MissingVolumeData;
import com.example.comiku.data.model.RecentLibraryVolumeData;
import com.example.comiku.data.repository.UserShelfRepository;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BaseDrawerActivity {
    public static final String EXTRA_HOME_NOTICE = "extra_home_notice";
    private ProgressBar barraCargaInicio;
    private LinearLayout contenedorCarruselTomosFaltantesInicio;
    private ImageButton botonIzquierdaTomosFaltantesInicio;
    private ImageButton botonDerechaTomosFaltantesInicio;
    private HorizontalScrollView scrollTomosFaltantesInicio;
    private LinearLayout contenedorTomosFaltantesInicio;
    private TextView textoEstadoTomosFaltantesInicio;
    private MissingVolumeCarouselComponent componenteCarruselFaltantes;
    private LinearLayout contenedorCarruselUltimosTomosInicio;
    private ImageButton botonIzquierdaUltimosTomosInicio;
    private ImageButton botonDerechaUltimosTomosInicio;
    private HorizontalScrollView scrollUltimosTomosInicio;
    private LinearLayout contenedorUltimosTomosInicio;
    private TextView textoEstadoUltimosTomosInicio;
    private RecentVolumeCarouselComponent componenteCarruselRecientes;
    private LinearLayout contenedorCarruselRecomendacionesInicio;
    private ImageButton botonIzquierdaRecomendacionesInicio;
    private ImageButton botonDerechaRecomendacionesInicio;
    private HorizontalScrollView scrollRecomendacionesInicio;
    private LinearLayout contenedorRecomendacionesInicio;
    private TextView textoEstadoRecomendacionesInicio;
    private RecommendationCarouselComponent componenteCarruselRecomendaciones;
    private HomePresentationCarouselComponent componenteCarruselPresentacion;

    // Inicializa el home usando el componente de navbar.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            openLoginAndClearStack();
            return;
        }
        setupDrawerShell(getString(R.string.inicio_titulo));
    }


    @Override
    protected int getScreenLayoutId() {
        return R.layout.activity_main;
    }

    // Ajusta textos del home al cargar contenido.
    @Override
    protected void onScreenContentReady() {
        barraCargaInicio = findViewById(R.id.barraCargaInicio);
        View vistaCarruselPresentacionInicio = findViewById(R.id.vistaCarruselPresentacionInicio);
        View indicadorPresentacionUno = findViewById(R.id.indicadorPresentacionInicioUno);
        View indicadorPresentacionDos = findViewById(R.id.indicadorPresentacionInicioDos);
        contenedorCarruselTomosFaltantesInicio = findViewById(R.id.contenedorCarruselTomosFaltantesInicio);
        botonIzquierdaTomosFaltantesInicio = findViewById(R.id.botonIzquierdaTomosFaltantesInicio);
        botonDerechaTomosFaltantesInicio = findViewById(R.id.botonDerechaTomosFaltantesInicio);
        scrollTomosFaltantesInicio = findViewById(R.id.scrollTomosFaltantesInicio);
        contenedorTomosFaltantesInicio = findViewById(R.id.contenedorTomosFaltantesInicio);
        textoEstadoTomosFaltantesInicio = findViewById(R.id.textoEstadoTomosFaltantesInicio);
        contenedorCarruselUltimosTomosInicio = findViewById(R.id.contenedorCarruselUltimosTomosInicio);
        botonIzquierdaUltimosTomosInicio = findViewById(R.id.botonIzquierdaUltimosTomosInicio);
        botonDerechaUltimosTomosInicio = findViewById(R.id.botonDerechaUltimosTomosInicio);
        scrollUltimosTomosInicio = findViewById(R.id.scrollUltimosTomosInicio);
        contenedorUltimosTomosInicio = findViewById(R.id.contenedorUltimosTomosInicio);
        textoEstadoUltimosTomosInicio = findViewById(R.id.textoEstadoUltimosTomosInicio);

        componenteCarruselPresentacion = new HomePresentationCarouselComponent(this);
        componenteCarruselFaltantes = new MissingVolumeCarouselComponent(this);
        componenteCarruselRecientes = new RecentVolumeCarouselComponent(this);
        componenteCarruselRecomendaciones = new RecommendationCarouselComponent(this);
        contenedorCarruselRecomendacionesInicio = findViewById(R.id.contenedorCarruselRecomendacionesInicio);
        botonIzquierdaRecomendacionesInicio = findViewById(R.id.botonIzquierdaRecomendacionesInicio);
        botonDerechaRecomendacionesInicio = findViewById(R.id.botonDerechaRecomendacionesInicio);
        scrollRecomendacionesInicio = findViewById(R.id.scrollRecomendacionesInicio);
        contenedorRecomendacionesInicio = findViewById(R.id.contenedorRecomendacionesInicio);
        textoEstadoRecomendacionesInicio = findViewById(R.id.textoEstadoRecomendacionesInicio);
        componenteCarruselPresentacion.bind(vistaCarruselPresentacionInicio);
        componenteCarruselPresentacion.setIndicatorViews(indicadorPresentacionUno, indicadorPresentacionDos);
        componenteCarruselPresentacion.setOnCreateComicClickListener(v -> openManualCreation());
        setupMissingCarouselScroll();
        loadHomeHighlights();
        mostrarConfirmacionPendiente();
    }


    @Override
    protected void onResume() {
        super.onResume();
        loadHomeHighlights();
    }

    // Configura el desplazamiento horizontal de los carruseles de inicio.
    private void setupMissingCarouselScroll() {
        botonIzquierdaTomosFaltantesInicio.setOnClickListener(v -> scrollTomosFaltantesInicio.smoothScrollBy(-420, 0));
        botonDerechaTomosFaltantesInicio.setOnClickListener(v -> scrollTomosFaltantesInicio.smoothScrollBy(420, 0));
        botonIzquierdaUltimosTomosInicio.setOnClickListener(v -> scrollUltimosTomosInicio.smoothScrollBy(-420, 0));
        botonDerechaUltimosTomosInicio.setOnClickListener(v -> scrollUltimosTomosInicio.smoothScrollBy(420, 0));
        botonIzquierdaRecomendacionesInicio.setOnClickListener(v -> scrollRecomendacionesInicio.smoothScrollBy(-420, 0));
        botonDerechaRecomendacionesInicio.setOnClickListener(v -> scrollRecomendacionesInicio.smoothScrollBy(420, 0));
    }

    // Carga los destacados del inicio para biblioteca.
    private void loadHomeHighlights() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            openLoginAndClearStack();
            return;
        }

        String uidUsuario = FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (TextUtils.isEmpty(uidUsuario)) {
            showMissingState(getString(R.string.inicio_tomos_faltantes_error), false);
            showRecentState(getString(R.string.inicio_ultimos_tomos_error), false);
            return;
        }

        barraCargaInicio.setVisibility(android.view.View.VISIBLE);
        UserShelfRepository.getHomeMissingVolumes(uidUsuario, 25)
                .addOnSuccessListener(this::renderHomeMissingVolumes)
                .addOnFailureListener(error -> {
                    barraCargaInicio.setVisibility(android.view.View.GONE);
                    showMissingState(getString(R.string.inicio_tomos_faltantes_error), false);
                });
        UserShelfRepository.getHomeRecentLibraryVolumes(uidUsuario, 20)
                .addOnSuccessListener(this::renderHomeRecentVolumes)
                .addOnFailureListener(error -> showRecentState(getString(R.string.inicio_ultimos_tomos_error), false));
        UserShelfRepository.getHomeRecommendations(uidUsuario, 20)
                .addOnSuccessListener(this::renderHomeRecommendations)
                .addOnFailureListener(error -> showRecommendationsState(getString(R.string.inicio_recomendaciones_error), false));
    }

    // Muestra el resultado de tomos faltantes en el home.
    private void renderHomeMissingVolumes(HomeMissingVolumesData datos) {
        barraCargaInicio.setVisibility(android.view.View.GONE);
        if (datos == null || !datos.tieneTomosEnBiblioteca) {
            showMissingState(getString(R.string.inicio_tomos_faltantes_sin_biblioteca), false);
            return;
        }

        List<MissingVolumeData> faltantes = datos.tomosFaltantes != null ? datos.tomosFaltantes : new ArrayList<>();
        if (faltantes.isEmpty()) {
            showMissingState(getString(R.string.tomos_faltantes_coleccion_al_dia), false);
            return;
        }

        textoEstadoTomosFaltantesInicio.setVisibility(android.view.View.GONE);
        contenedorCarruselTomosFaltantesInicio.setVisibility(android.view.View.VISIBLE);
        componenteCarruselFaltantes.renderVolumes(
                contenedorTomosFaltantesInicio,
                faltantes,
                true,
                this::openMissingVolumeDetail
        );
    }

    // Muestra un mensaje de estado en la seccion de tomos faltantes.
    private void showMissingState(String mensaje, boolean mostrarCarrusel) {
        contenedorCarruselTomosFaltantesInicio.setVisibility(
                mostrarCarrusel ? android.view.View.VISIBLE : android.view.View.GONE
        );
        textoEstadoTomosFaltantesInicio.setText(mensaje);
        textoEstadoTomosFaltantesInicio.setVisibility(android.view.View.VISIBLE);
        if (!mostrarCarrusel) {
            contenedorTomosFaltantesInicio.removeAllViews();
        }
    }

    // Muestra la seccion de ultimos tomos agregados.
    private void renderHomeRecentVolumes(HomeRecentVolumesData datos) {
        barraCargaInicio.setVisibility(android.view.View.GONE);
        if (datos == null || !datos.tieneTomosEnBiblioteca) {
            showRecentState(getString(R.string.inicio_ultimos_tomos_sin_biblioteca), false);
            return;
        }

        List<RecentLibraryVolumeData> recientes = datos.tomosRecientes != null ? datos.tomosRecientes : new ArrayList<>();
        if (recientes.isEmpty()) {
            showRecentState(getString(R.string.inicio_ultimos_tomos_sin_biblioteca), false);
            return;
        }

        textoEstadoUltimosTomosInicio.setVisibility(android.view.View.GONE);
        contenedorCarruselUltimosTomosInicio.setVisibility(android.view.View.VISIBLE);
        componenteCarruselRecientes.renderVolumes(
                contenedorUltimosTomosInicio,
                recientes,
                this::openRecentVolumeDetail
        );
    }

    // Muestra un mensaje de estado en la seccion de tomos recientes.
    private void showRecentState(String mensaje, boolean mostrarCarrusel) {
        contenedorCarruselUltimosTomosInicio.setVisibility(
                mostrarCarrusel ? android.view.View.VISIBLE : android.view.View.GONE
        );
        textoEstadoUltimosTomosInicio.setText(mensaje);
        textoEstadoUltimosTomosInicio.setVisibility(android.view.View.VISIBLE);
        if (!mostrarCarrusel) {
            contenedorUltimosTomosInicio.removeAllViews();
        }
    }

    // Abre la pantalla de detalle para un tomo faltante.
    private void openMissingVolumeDetail(MissingVolumeData tomoFaltante) {
        if (tomoFaltante == null || tomoFaltante.tomo == null) {
            return;
        }

        Intent pantallaDetalleTomo = new Intent(this, VolumeDetailActivity.class);
        pantallaDetalleTomo.putExtra(VolumeDetailActivity.EXTRA_COMIC_ID, tomoFaltante.comicId);
        pantallaDetalleTomo.putExtra(VolumeDetailActivity.EXTRA_VOLUME_ID, tomoFaltante.tomo.id);
        startActivity(pantallaDetalleTomo);
    }

    // Abre la pantalla de detalle para un tomo reciente.
    private void openRecentVolumeDetail(RecentLibraryVolumeData tomoReciente) {
        if (tomoReciente == null || tomoReciente.tomo == null) {
            return;
        }

        Intent pantallaDetalleTomo = new Intent(this, VolumeDetailActivity.class);
        pantallaDetalleTomo.putExtra(VolumeDetailActivity.EXTRA_COMIC_ID, tomoReciente.comicId);
        pantallaDetalleTomo.putExtra(VolumeDetailActivity.EXTRA_VOLUME_ID, tomoReciente.tomo.id);
        startActivity(pantallaDetalleTomo);
    }

    // Muestra la seccion de recomendaciones personalizadas.
    private void renderHomeRecommendations(HomeRecommendationsData datos) {
        if (datos == null || !datos.tieneItemsEnBiblioteca) {
            showRecommendationsState(getString(R.string.inicio_recomendaciones_sin_biblioteca), false);
            return;
        }

        List<ComicRecommendationData> recomendaciones = datos.recomendaciones != null
                ? datos.recomendaciones : new ArrayList<>();
        if (recomendaciones.isEmpty()) {
            showRecommendationsState(getString(R.string.inicio_recomendaciones_vacias), false);
            return;
        }

        textoEstadoRecomendacionesInicio.setVisibility(android.view.View.GONE);
        contenedorCarruselRecomendacionesInicio.setVisibility(android.view.View.VISIBLE);
        componenteCarruselRecomendaciones.renderRecommendations(
                contenedorRecomendacionesInicio,
                recomendaciones,
                this::openRecommendedComic
        );
    }

    // Muestra un mensaje de estado en la seccion de recomendaciones.
    private void showRecommendationsState(String mensaje, boolean mostrarCarrusel) {
        contenedorCarruselRecomendacionesInicio.setVisibility(
                mostrarCarrusel ? android.view.View.VISIBLE : android.view.View.GONE
        );
        textoEstadoRecomendacionesInicio.setText(mensaje);
        textoEstadoRecomendacionesInicio.setVisibility(android.view.View.VISIBLE);
        if (!mostrarCarrusel) {
            contenedorRecomendacionesInicio.removeAllViews();
        }
    }

    // Abre la pantalla de detalle del comic recomendado.
    private void openRecommendedComic(String comicId) {
        if (android.text.TextUtils.isEmpty(comicId)) {
            return;
        }
        Intent pantallaDetalleComic = new Intent(this, ComicDetailActivity.class);
        pantallaDetalleComic.putExtra(ComicDetailActivity.EXTRA_COMIC_ID, comicId);
        startActivity(pantallaDetalleComic);
    }

    // Abre la pantalla de carga manual de comic.
    private void openManualCreation() {
        Intent pantallaCreacionManual = new Intent(this, ManualCreationActivity.class);
        startActivity(pantallaCreacionManual);
    }

    // Muestra un aviso recibido al volver al inicio.
    private void mostrarConfirmacionPendiente() {
        Intent intentActual = getIntent();
        if (intentActual == null) {
            return;
        }
        String mensaje = intentActual.getStringExtra(EXTRA_HOME_NOTICE);
        if (TextUtils.isEmpty(mensaje)) {
            return;
        }
        ToastUtils.showTextToast(this, mensaje, Toast.LENGTH_LONG);
        intentActual.removeExtra(EXTRA_HOME_NOTICE);
    }
}

