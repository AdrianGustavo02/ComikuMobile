package com.example.comiku.core.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;

import com.example.comiku.R;

import java.util.ArrayList;
import java.util.List;

public final class HomePresentationCarouselComponent {
    private static final long AUTO_SLIDE_DELAY_MS = 12000L;
    private static final String SLIDE_UNO_NOMBRE = "home_presentation_slide_1";
    private static final String SLIDE_DOS_NOMBRE = "home_presentation_slide_2";

    private final Context contexto;
    private final List<SlideData> slides = new ArrayList<>();
    private final Handler manejadorPrincipal = new Handler(Looper.getMainLooper());
    private final Runnable tareaAutoSlide = this::showNextSlideAutomatic;

    private View contenedorPresentacion;
    private ImageView imagenSlide;
    private TextView textoTitulo;
    private TextView textoDescripcion;
    private TextView textoApoyo;
    private Button botonCrearComic;
    private View.OnClickListener listenerBotonCrearComic;
    private View indicadorUno;
    private View indicadorDos;
    private int indiceActual;
    private boolean autoSlideActivo;

    // Crea el componente del carrusel principal del inicio.
    public HomePresentationCarouselComponent(Context contexto) {
        this.contexto = contexto;
    }

    // Conecta el componente con la vista y prepara los indicadores.
    public void bind(View raiz) {
        if (raiz == null) {
            return;
        }

        contenedorPresentacion = raiz.findViewById(R.id.contenedorPresentacionInicio);
        imagenSlide = raiz.findViewById(R.id.imagenPresentacionInicio);
        textoTitulo = raiz.findViewById(R.id.textoTituloPresentacionInicio);
        textoDescripcion = raiz.findViewById(R.id.textoDescripcionPresentacionInicio);
        textoApoyo = raiz.findViewById(R.id.textoApoyoPresentacionInicio);
        botonCrearComic = raiz.findViewById(R.id.botonCrearComicPresentacionInicio);
        indicadorUno = raiz.findViewById(R.id.indicadorPresentacionInicioUno);
        indicadorDos = raiz.findViewById(R.id.indicadorPresentacionInicioDos);

        if (contenedorPresentacion != null) {
            contenedorPresentacion.setOnTouchListener(createSwipeTouchListener());
        }

        setupIndicators();

        if (botonCrearComic != null) {
            botonCrearComic.setOnClickListener(v -> {
                if (listenerBotonCrearComic != null) {
                    listenerBotonCrearComic.onClick(v);
                }
            });
        }

        setSlides(createDefaultSlides());
    }

    // Conecta indicadores que viven fuera del carrusel.
    public void setIndicatorViews(View indicadorPrimero, View indicadorSegundo) {
        indicadorUno = indicadorPrimero;
        indicadorDos = indicadorSegundo;
        setupIndicators();
        updateIndicators();
    }
    // Define la accion del boton para crear comic.
    public void setOnCreateComicClickListener(View.OnClickListener listener) {
        listenerBotonCrearComic = listener;
    }

    // Prepara eventos de los indicadores del carrusel.
    private void setupIndicators() {
        if (indicadorUno != null) {
            indicadorUno.setClickable(true);
            indicadorUno.setFocusable(true);
            indicadorUno.setOnClickListener(v -> goToSlide(0, true));
        }
        if (indicadorDos != null) {
            indicadorDos.setClickable(true);
            indicadorDos.setFocusable(true);
            indicadorDos.setOnClickListener(v -> goToSlide(1, true));
        }
    }
    // Inicia el cambio automatico de slides.
    public void startAutoSlide() {
        autoSlideActivo = true;
        resetAutoSlideTimer();
    }

    // Detiene el cambio automatico de slides.
    public void stopAutoSlide() {
        autoSlideActivo = false;
        manejadorPrincipal.removeCallbacks(tareaAutoSlide);
    }

    // Carga los slides iniciales usando nombres fijos de recursos.
    public void setSlides(List<SlideData> nuevosSlides) {
        slides.clear();
        if (nuevosSlides != null) {
            slides.addAll(nuevosSlides);
        }
        indiceActual = 0;
        renderCurrentSlide();
        resetAutoSlideTimer();
    }

    // Va al slide pedido cuando se toca un indicador.
    private void goToSlide(int indiceDestino, boolean reiniciarTimer) {
        if (slides.isEmpty()) {
            return;
        }
        if (indiceDestino < 0 || indiceDestino >= slides.size()) {
            return;
        }
        indiceActual = indiceDestino;
        renderCurrentSlide();
        if (reiniciarTimer) {
            resetAutoSlideTimer();
        }
    }

    // Avanza al siguiente slide cuando lo hace el temporizador.
    private void showNextSlideAutomatic() {
        if (!autoSlideActivo || slides.size() < 2) {
            return;
        }
        indiceActual = (indiceActual + 1) % slides.size();
        renderCurrentSlide();
        resetAutoSlideTimer();
    }

    // Avanza al siguiente slide por accion del usuario.
    private void showNextSlideManual() {
        if (slides.isEmpty()) {
            return;
        }
        indiceActual = (indiceActual + 1) % slides.size();
        renderCurrentSlide();
        resetAutoSlideTimer();
    }

    // Vuelve al slide anterior por accion del usuario.
    private void showPreviousSlideManual() {
        if (slides.isEmpty()) {
            return;
        }
        indiceActual = (indiceActual - 1 + slides.size()) % slides.size();
        renderCurrentSlide();
        resetAutoSlideTimer();
    }

    // Programa nuevamente el temporizador del auto slide.
    private void resetAutoSlideTimer() {
        manejadorPrincipal.removeCallbacks(tareaAutoSlide);
        if (autoSlideActivo && slides.size() > 1) {
            manejadorPrincipal.postDelayed(tareaAutoSlide, AUTO_SLIDE_DELAY_MS);
        }
    }

    // Crea el gesto de deslizamiento horizontal del carrusel.
    private View.OnTouchListener createSwipeTouchListener() {
        GestureDetector detectorGestos = new GestureDetector(
                contexto,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent evento) {
                        return true;
                    }

                    @Override
                    public boolean onFling(MotionEvent eventoInicial, MotionEvent eventoFinal, float velocidadX, float velocidadY) {
                        if (eventoInicial == null || eventoFinal == null) {
                            return false;
                        }

                        float distanciaX = eventoFinal.getX() - eventoInicial.getX();
                        float distanciaY = eventoFinal.getY() - eventoInicial.getY();

                        if (Math.abs(distanciaX) < 80f) {
                            return false;
                        }
                        if (Math.abs(distanciaX) <= Math.abs(distanciaY)) {
                            return false;
                        }
                        if (Math.abs(velocidadX) < 120f) {
                            return false;
                        }

                        if (distanciaX < 0f) {
                            showNextSlideManual();
                        } else {
                            showPreviousSlideManual();
                        }
                        return true;
                    }
                }
        );

        return (view, evento) -> detectorGestos.onTouchEvent(evento);
    }

    // Muestra en pantalla el slide actual.
    private void renderCurrentSlide() {
        if (slides.isEmpty()) {
            applySlide(null);
            return;
        }
        applySlide(slides.get(indiceActual));
    }

    // Aplica imagen, textos e indicadores del slide activo.
    private void applySlide(SlideData slideActual) {
        if (imagenSlide != null) {
            @DrawableRes int recursoImagen = resolveImageResource(
                    slideActual != null ? slideActual.nombreRecursoImagen : null
            );
            imagenSlide.setImageResource(recursoImagen);
        }
        if (textoTitulo != null) {
            String titulo = slideActual != null ? slideActual.titulo : null;
            textoTitulo.setText(titulo);
            textoTitulo.setVisibility(TextUtils.isEmpty(titulo) ? View.GONE : View.VISIBLE);
        }
        if (textoDescripcion != null) {
            String descripcion = slideActual != null ? slideActual.descripcion : null;
            textoDescripcion.setText(descripcion);
            applyDescriptionStyle(slideActual);
            textoDescripcion.setVisibility(TextUtils.isEmpty(descripcion) ? View.GONE : View.VISIBLE);
        }
        if (textoApoyo != null) {
            String textoSecundario = slideActual != null ? slideActual.textoSecundario : null;
            textoApoyo.setText(textoSecundario);
            textoApoyo.setVisibility(TextUtils.isEmpty(textoSecundario) ? View.GONE : View.VISIBLE);
        }
        if (botonCrearComic != null) {
            boolean mostrarBoton = slideActual != null
                    && SLIDE_DOS_NOMBRE.equals(slideActual.nombreRecursoImagen);
            botonCrearComic.setVisibility(mostrarBoton ? View.VISIBLE : View.GONE);
        }
        updateIndicators();
    }

    // Ajusta el estilo de la descripcion segun el slide activo.
    private void applyDescriptionStyle(SlideData slideActual) {
        if (textoDescripcion == null) {
            return;
        }

        boolean esSlideDestacado = slideActual != null
                && (SLIDE_UNO_NOMBRE.equals(slideActual.nombreRecursoImagen)
                || SLIDE_DOS_NOMBRE.equals(slideActual.nombreRecursoImagen));

        if (esSlideDestacado) {
            textoDescripcion.setTextColor(contexto.getColor(android.R.color.white));
            textoDescripcion.setTextSize(22f);
            textoDescripcion.setTypeface(null, android.graphics.Typeface.BOLD);
            return;
        }

        textoDescripcion.setTextColor(contexto.getColor(R.color.carousel_subtitle));
        textoDescripcion.setTextSize(15f);
        textoDescripcion.setTypeface(null, android.graphics.Typeface.NORMAL);
    }

    // Actualiza el estado visual de los puntos inferiores.
    private void updateIndicators() {
        updateIndicator(indicadorUno, indiceActual == 0);
        updateIndicator(indicadorDos, indiceActual == 1);
    }

    // Cambia el fondo del punto segun el slide activo.
    private void updateIndicator(View indicador, boolean activo) {
        if (indicador == null) {
            return;
        }
        indicador.setBackgroundResource(
                activo
                        ? R.drawable.bg_home_presentation_indicator_active
                        : R.drawable.bg_home_presentation_indicator_inactive
        );
    }

    // Resuelve una imagen por nombre y usa placeholder si aun no existe.
    @DrawableRes
    private int resolveImageResource(String nombreRecurso) {
        if (!TextUtils.isEmpty(nombreRecurso)) {
            int idRecurso = contexto.getResources().getIdentifier(
                    nombreRecurso,
                    "drawable",
                    contexto.getPackageName()
            );
            if (idRecurso != 0) {
                return idRecurso;
            }
        }
        return R.drawable.bg_home_presentation_placeholder;
    }

    // Crea los dos slides base del carrusel.
    private List<SlideData> createDefaultSlides() {
        List<SlideData> slidesBase = new ArrayList<>();
        slidesBase.add(new SlideData(
                SLIDE_UNO_NOMBRE,
                contexto.getString(R.string.inicio_presentacion_slide_uno_titulo),
                contexto.getString(R.string.inicio_presentacion_slide_uno_descripcion),
                contexto.getString(R.string.inicio_presentacion_slide_uno_texto_secundario)
        ));
        slidesBase.add(new SlideData(
                SLIDE_DOS_NOMBRE,
                contexto.getString(R.string.inicio_presentacion_slide_dos_titulo),
                contexto.getString(R.string.inicio_presentacion_slide_dos_descripcion),
                contexto.getString(R.string.inicio_presentacion_slide_dos_texto_secundario)
        ));
        return slidesBase;
    }

    // Guarda los datos simples de cada slide.
    public static final class SlideData {
        public final String nombreRecursoImagen;
        public final String titulo;
        public final String descripcion;
        public final String textoSecundario;

        // Crea un slide con imagen y textos.
        public SlideData(String nombreRecursoImagen, String titulo, String descripcion, String textoSecundario) {
            this.nombreRecursoImagen = nombreRecursoImagen;
            this.titulo = titulo;
            this.descripcion = descripcion;
            this.textoSecundario = textoSecundario;
        }
    }
}