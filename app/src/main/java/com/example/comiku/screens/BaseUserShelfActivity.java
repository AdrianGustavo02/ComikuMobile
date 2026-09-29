package com.example.comiku.screens;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.StringRes;

import com.example.comiku.R;

public abstract class BaseUserShelfActivity extends BaseDrawerActivity {
    protected TextView textoTituloListaUsuario;
    protected LinearLayout contenedorControlesListaUsuario;
    protected TextView textoErrorListaUsuario;
    protected ProgressBar barraCargaListaUsuario;
    protected TextView textoVacioListaUsuario;
    protected LinearLayout contenedorResultadosListaUsuario;

    private boolean listaPreparada;

    // Prepara la pantalla comun para listas del usuario.
    @Override
    protected void onScreenContentReady() {
        bindViews();
        textoTituloListaUsuario.setText(getShelfTitleText());
        setupShelfControls();
        listaPreparada = true;
    }

    // Recarga la lista cuando la pantalla vuelve a mostrarse.
    @Override
    protected void onResume() {
        super.onResume();
        if (listaPreparada) {
            refreshData();
        }
    }

    // Devuelve el titulo visible de la lista.
    @StringRes
    protected abstract int getShelfTitle();

    // Devuelve el titulo visible ya resuelto.
    protected CharSequence getShelfTitleText() {
        return getString(getShelfTitle());
    }

    // Devuelve el mensaje mientras se cargan datos.
    @StringRes
    protected abstract int getLoadingMessage();

    // Devuelve el mensaje cuando la lista esta vacia.
    @StringRes
    protected abstract int getEmptyMessage();

    // Configura los controles propios de cada lista.
    protected abstract void setupShelfControls();

    // Carga los datos de la lista.
    protected abstract void refreshData();

    // Devuelve el layout de la lista actual.
    @Override
    protected abstract int getScreenLayoutId();

    // Vincula los controles comunes.
    private void bindViews() {
        textoTituloListaUsuario = findViewById(R.id.textoTituloListaUsuario);
        contenedorControlesListaUsuario = findViewById(R.id.contenedorControlesListaUsuario);
        textoErrorListaUsuario = findViewById(R.id.textoErrorListaUsuario);
        barraCargaListaUsuario = findViewById(R.id.barraCargaListaUsuario);
        textoVacioListaUsuario = findViewById(R.id.textoVacioListaUsuario);
        contenedorResultadosListaUsuario = findViewById(R.id.contenedorResultadosListaUsuario);
    }

    // Muestra o esconde la carga.
    protected void setLoading(boolean cargando) {
        barraCargaListaUsuario.setVisibility(cargando ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    // Muestra un error en la pantalla.
    protected void showError(String mensaje) {
        textoErrorListaUsuario.setText(mensaje);
        textoErrorListaUsuario.setTextSize(16f);
        textoErrorListaUsuario.setTextColor(0xFF232323);
        textoErrorListaUsuario.setGravity(Gravity.CENTER);
        textoErrorListaUsuario.setPadding(0, dpToPx(20), 0, 0);
        textoErrorListaUsuario.setVisibility(android.view.View.VISIBLE);
    }

    // Limpia el error de la pantalla.
    protected void clearError() {
        textoErrorListaUsuario.setText("");
        textoErrorListaUsuario.setVisibility(android.view.View.GONE);
    }

    // Muestra o esconde el estado vacio.
    protected void showEmptyState(String mensaje) {
        textoVacioListaUsuario.setText(mensaje);
        textoVacioListaUsuario.setTextSize(16f);
        textoVacioListaUsuario.setTextColor(0xFF232323);
        textoVacioListaUsuario.setGravity(Gravity.CENTER);
        textoVacioListaUsuario.setPadding(0, dpToPx(20), 0, 0);
        textoVacioListaUsuario.setVisibility(android.view.View.VISIBLE);
    }

    // Limpia el estado vacio.
    protected void clearEmptyState() {
        textoVacioListaUsuario.setText("");
        textoVacioListaUsuario.setVisibility(android.view.View.GONE);
    }

    // Limpia los resultados visibles.
    protected void clearResults() {
        contenedorResultadosListaUsuario.removeAllViews();
    }

    // Limpia los controles visibles.
    protected void clearControls() {
        contenedorControlesListaUsuario.removeAllViews();
    }

    // Agrega un control a la zona superior.
    protected void addControlView(android.view.View view) {
        contenedorControlesListaUsuario.addView(view);
    }

    // Agrega una vista de resultado a la lista.
    protected void addResultView(android.view.View view) {
        contenedorResultadosListaUsuario.addView(view);
    }

    // Crea un subtitulo para una seccion.
    protected TextView createSectionLabel(String texto) {
        TextView etiqueta = new TextView(this);
        etiqueta.setText(texto);
        etiqueta.setTextSize(17f);
        etiqueta.setTypeface(null, android.graphics.Typeface.BOLD);
        etiqueta.setTextColor(0xFF232323);
        etiqueta.setPadding(0, 0, 0, dpToPx(6));
        return etiqueta;
    }

    // Crea un campo de texto simple.
    protected TextView createTextLine(String texto, boolean destacado) {
        TextView linea = new TextView(this);
        linea.setText(texto);
        linea.setTextSize(destacado ? 16f : 14f);
        linea.setTextColor(destacado ? getResources().getColor(android.R.color.black) : getResources().getColor(android.R.color.darker_gray));
        return linea;
    }

    // Crea un boton reutilizable.
    protected Button createActionButton(String texto) {
        Button boton = new Button(this);
        boton.setText(texto);
        return boton;
    }

    // Crea una tarjeta simple para mostrar contenido.
    protected LinearLayout createCardContainer() {
        LinearLayout contenedor = new LinearLayout(this);
        contenedor.setOrientation(LinearLayout.VERTICAL);
        contenedor.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dpToPx(12);
        contenedor.setLayoutParams(params);
        return contenedor;
    }

    // Crea una imagen reutilizable para portadas.
    protected ImageView createCoverImage() {
        ImageView imagen = new ImageView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(180)
        );
        params.bottomMargin = dpToPx(10);
        imagen.setLayoutParams(params);
        imagen.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return imagen;
    }

    // Convierte una cadena dataUrl en un bitmap.
    protected Bitmap decodeDataUrl(String dataUrl) {
        if (dataUrl == null || dataUrl.trim().isEmpty()) {
            return null;
        }

        int indiceComa = dataUrl.indexOf(',');
        if (indiceComa < 0 || indiceComa >= dataUrl.length() - 1) {
            return null;
        }

        try {
            byte[] bytesImagen = Base64.decode(dataUrl.substring(indiceComa + 1), Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytesImagen, 0, bytesImagen.length);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    // Convierte dp a pixeles.
    protected int dpToPx(int dp) {
        float densidad = getResources().getDisplayMetrics().density;
        return Math.round(dp * densidad);
    }
}
