package com.example.comiku.core.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.comiku.R;
import com.example.comiku.data.model.RecentLibraryVolumeData;
import com.example.comiku.data.model.VolumeDetailData;

import java.util.List;

public final class RecentVolumeCarouselComponent {
    private final Context contexto;

    public interface OnRecentVolumeClickListener {
        void onVolumeClick(RecentLibraryVolumeData tomoReciente);
    }

    // Crea el componente para el carrusel de tomos recientes.
    public RecentVolumeCarouselComponent(Context contexto) {
        this.contexto = contexto;
    }

    // Dibuja solo portadas en una lista horizontal de tomos recientes.
    public void renderVolumes(
            LinearLayout contenedor,
            List<RecentLibraryVolumeData> tomosRecientes,
            OnRecentVolumeClickListener listener
    ) {
        contenedor.removeAllViews();
        if (tomosRecientes == null || tomosRecientes.isEmpty()) {
            return;
        }

        for (RecentLibraryVolumeData tomoReciente : tomosRecientes) {
            contenedor.addView(createCover(tomoReciente, listener));
        }
    }

    // Crea una portada clickeable para abrir detalle.
    private View createCover(RecentLibraryVolumeData tomoReciente, OnRecentVolumeClickListener listener) {
        FrameLayout tarjeta = new FrameLayout(contexto);
        tarjeta.setBackground(contexto.getDrawable(R.drawable.bg_volume_cover_card));
        LinearLayout.LayoutParams paramsTarjeta = new LinearLayout.LayoutParams(dpToPx(150), ViewGroup.LayoutParams.WRAP_CONTENT);
        paramsTarjeta.setMarginEnd(dpToPx(10));
        tarjeta.setLayoutParams(paramsTarjeta);
        tarjeta.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));
        tarjeta.setFocusable(true);

        LinearLayout contenido = new LinearLayout(contexto);
        contenido.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams paramsContenido = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        contenido.setLayoutParams(paramsContenido);

        ImageView imagenPortada = new ImageView(contexto);
        LinearLayout.LayoutParams paramsImagen = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(200)
        );
        imagenPortada.setLayoutParams(paramsImagen);
        imagenPortada.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imagenPortada.setBackground(contexto.getDrawable(R.drawable.bg_volume_cover_image));
        imagenPortada.setContentDescription(contexto.getString(R.string.portada_del_tomo));
        imagenPortada.setClipToOutline(true);

        Bitmap bitmap = decodeDataUrl(tomoReciente != null && tomoReciente.tomo != null
                ? tomoReciente.tomo.getPortadaDataUrl()
                : null);
        if (bitmap != null) {
            imagenPortada.setImageBitmap(bitmap);
        } else {
            imagenPortada.setImageResource(R.drawable.default_profile_picture);
        }

        contenido.addView(imagenPortada);

        // Nombre del comic (negrita y blanco)
        TextView textoComic = new TextView(contexto);
        textoComic.setText(tomoReciente != null ? String.valueOf(tomoReciente.comicNombre) : "");
        textoComic.setTextSize(12f);
        textoComic.setTypeface(null, android.graphics.Typeface.BOLD);
        textoComic.setMaxLines(3);
        textoComic.setEllipsize(TextUtils.TruncateAt.END);
        textoComic.setTextColor(contexto.getColor(android.R.color.white));
        textoComic.setPadding(0, dpToPx(10), 0, 0);
        contenido.addView(textoComic);

        // Tomo y número (color de descripción)
        TextView textoTomo = new TextView(contexto);
        textoTomo.setText(getVolumeLabel(tomoReciente != null ? tomoReciente.tomo : null));
        textoTomo.setTextSize(11f);
        textoTomo.setMaxLines(2);
        textoTomo.setEllipsize(TextUtils.TruncateAt.END);
        textoTomo.setTextColor(contexto.getColor(R.color.carousel_subtitle));
        textoTomo.setPadding(0, dpToPx(4), 0, 0);
        contenido.addView(textoTomo);

        // ISBN (color de descripción)
        TextView textoIsbn = new TextView(contexto);
        String isbnText = "ISBN: " + (tomoReciente != null && tomoReciente.tomo != null && tomoReciente.tomo.isbn != null
                ? String.valueOf(tomoReciente.tomo.isbn)
                : "N/A");
        textoIsbn.setText(isbnText);
        textoIsbn.setTextSize(11f);
        textoIsbn.setMaxLines(2);
        textoIsbn.setEllipsize(TextUtils.TruncateAt.END);
        textoIsbn.setTextColor(contexto.getColor(R.color.carousel_subtitle));
        textoIsbn.setPadding(0, dpToPx(2), 0, 0);
        contenido.addView(textoIsbn);

        tarjeta.addView(contenido);

        tarjeta.setOnClickListener(v -> {
            if (listener != null && tomoReciente != null) {
                listener.onVolumeClick(tomoReciente);
            }
        });
        return tarjeta;
    }

    // Devuelve la etiqueta corta para el numero de tomo.
    private String getVolumeLabel(VolumeDetailData tomo) {
        if (tomo == null) {
            return "";
        }
        if (Boolean.TRUE.equals(tomo.tomoUnico)) {
            return contexto.getString(R.string.tomos_faltantes_tomo_unico);
        }
        if (tomo.numeroTomo != null) {
            return contexto.getString(R.string.tomos_faltantes_tomo_numero, tomo.numeroTomo);
        }
        return contexto.getString(R.string.tomos_faltantes_tomo_sin_numero);
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

    // Convierte dp a pixeles para tamaños de portada.
    private int dpToPx(int dp) {
        float densidad = contexto.getResources().getDisplayMetrics().density;
        return Math.round(dp * densidad);
    }
}
