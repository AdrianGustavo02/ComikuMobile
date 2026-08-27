package com.example.comiku.core.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.comiku.R;
import com.example.comiku.data.model.ComicRecommendationData;

import java.util.List;

// Componente para mostrar el carrusel de recomendaciones en el inicio.
public final class RecommendationCarouselComponent {
    private final Context contexto;

    public interface OnComicClickListener {
        void onComicClick(String comicId);
    }

    // Crea el componente de carrusel de recomendaciones.
    public RecommendationCarouselComponent(Context contexto) {
        this.contexto = contexto;
    }

    // Dibuja las tarjetas de recomendaciones en el contenedor dado.
    public void renderRecommendations(
            LinearLayout contenedor,
            List<ComicRecommendationData> recomendaciones,
            OnComicClickListener listener
    ) {
        contenedor.removeAllViews();
        if (recomendaciones == null || recomendaciones.isEmpty()) {
            return;
        }

        for (ComicRecommendationData recomendacion : recomendaciones) {
            contenedor.addView(createCard(recomendacion, listener));
        }
    }

    // Crea una tarjeta vertical con portada, nombre, autores y generos coincidentes.
    private View createCard(ComicRecommendationData recomendacion, OnComicClickListener listener) {
        LinearLayout tarjeta = new LinearLayout(contexto);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dpToPx(160), ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(dpToPx(12));
        tarjeta.setLayoutParams(params);
        tarjeta.setOrientation(LinearLayout.VERTICAL);

        // Portada del tomo destacado.
        ImageView imagenPortada = new ImageView(contexto);
        LinearLayout.LayoutParams portadaParams = new LinearLayout.LayoutParams(dpToPx(160), dpToPx(220));
        imagenPortada.setLayoutParams(portadaParams);
        imagenPortada.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imagenPortada.setContentDescription(contexto.getString(R.string.portada_del_tomo));

        String dataUrl = recomendacion != null && recomendacion.tomoDestacado != null
                ? recomendacion.tomoDestacado.getPortadaDataUrl()
                : null;
        Bitmap bitmap = decodeDataUrl(dataUrl);
        if (bitmap != null) {
            imagenPortada.setImageBitmap(bitmap);
        } else {
            imagenPortada.setImageResource(R.drawable.default_profile_picture);
        }
        tarjeta.addView(imagenPortada);

        // Nombre del comic.
        TextView textoNombre = new TextView(contexto);
        LinearLayout.LayoutParams nombreParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nombreParams.topMargin = dpToPx(6);
        textoNombre.setLayoutParams(nombreParams);
        textoNombre.setText(recomendacion != null ? recomendacion.comicNombre : "");
        textoNombre.setTextSize(13f);
        textoNombre.setTypeface(null, android.graphics.Typeface.BOLD);
        textoNombre.setMaxLines(2);
        textoNombre.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tarjeta.addView(textoNombre);

        // Autores del comic.
        TextView textoAutores = new TextView(contexto);
        LinearLayout.LayoutParams autoresParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        autoresParams.topMargin = dpToPx(2);
        textoAutores.setLayoutParams(autoresParams);
        String autores = recomendacion != null && recomendacion.comicAutores != null
                && !recomendacion.comicAutores.isEmpty()
                ? String.join(", ", recomendacion.comicAutores)
                : contexto.getString(R.string.autores_desconocidos);
        textoAutores.setText(autores);
        textoAutores.setTextSize(11f);
        textoAutores.setMaxLines(1);
        textoAutores.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tarjeta.addView(textoAutores);

        // Generos coincidentes o mensaje general.
        TextView textoGeneros = new TextView(contexto);
        LinearLayout.LayoutParams generosParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        generosParams.topMargin = dpToPx(2);
        textoGeneros.setLayoutParams(generosParams);
        String generos = recomendacion != null && recomendacion.generosCoincidentes != null
                && !recomendacion.generosCoincidentes.isEmpty()
                ? String.join(", ", recomendacion.generosCoincidentes)
                : contexto.getString(R.string.inicio_recomendaciones_sugerencia_general);
        textoGeneros.setText(generos);
        textoGeneros.setTextSize(11f);
        textoGeneros.setMaxLines(1);
        textoGeneros.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tarjeta.addView(textoGeneros);

        // Click abre el detalle del comic.
        String comicIdFinal = recomendacion != null ? recomendacion.comicId : null;
        tarjeta.setOnClickListener(v -> {
            if (listener != null && !TextUtils.isEmpty(comicIdFinal)) {
                listener.onComicClick(comicIdFinal);
            }
        });

        return tarjeta;
    }

    // Convierte dataUrl a bitmap para mostrar portadas.
    private Bitmap decodeDataUrl(String dataUrl) {
        if (TextUtils.isEmpty(dataUrl)) {
            return null;
        }
        int indiceComa = dataUrl.indexOf(',');
        if (indiceComa < 0 || indiceComa >= dataUrl.length() - 1) {
            return null;
        }
        String base64 = dataUrl.substring(indiceComa + 1);
        try {
            byte[] bytesImagen = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytesImagen, 0, bytesImagen.length);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    // Convierte dp a pixeles para los tamanos de las tarjetas.
    private int dpToPx(int dp) {
        float densidad = contexto.getResources().getDisplayMetrics().density;
        return Math.round(dp * densidad);
    }
}
