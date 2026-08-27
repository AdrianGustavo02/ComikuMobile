package com.example.comiku.core.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.comiku.R;
import com.example.comiku.data.model.MissingVolumeData;
import com.example.comiku.data.model.VolumeDetailData;

import java.util.List;

public final class MissingVolumeCarouselComponent {
    private final Context contexto;

    public interface OnMissingVolumeClickListener {
        void onVolumeClick(MissingVolumeData tomoFaltante);
    }

    // Crea el componente para renderizar carruseles de tomos faltantes.
    public MissingVolumeCarouselComponent(Context contexto) {
        this.contexto = contexto;
    }

    // Dibuja una lista de tomos faltantes dentro de un contenedor horizontal.
    public void renderVolumes(
            LinearLayout contenedor,
            List<MissingVolumeData> tomosFaltantes,
            boolean mostrarNombreComic,
            OnMissingVolumeClickListener listener
    ) {
        contenedor.removeAllViews();
        if (tomosFaltantes == null || tomosFaltantes.isEmpty()) {
            return;
        }

        for (MissingVolumeData tomoFaltante : tomosFaltantes) {
            contenedor.addView(createVolumeCard(tomoFaltante, mostrarNombreComic, listener));
        }
    }

    // Crea una tarjeta de portada para un tomo faltante.
    private View createVolumeCard(
            MissingVolumeData tomoFaltante,
            boolean mostrarNombreComic,
            OnMissingVolumeClickListener listener
    ) {
        LinearLayout tarjeta = new LinearLayout(contexto);
        tarjeta.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams paramsTarjeta = new LinearLayout.LayoutParams(dpToPx(150), ViewGroup.LayoutParams.WRAP_CONTENT);
        paramsTarjeta.setMarginEnd(dpToPx(10));
        tarjeta.setLayoutParams(paramsTarjeta);
        tarjeta.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));

        ImageView imagenPortada = new ImageView(contexto);
        LinearLayout.LayoutParams paramsImagen = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(210)
        );
        imagenPortada.setLayoutParams(paramsImagen);
        imagenPortada.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap bitmap = decodeDataUrl(tomoFaltante != null && tomoFaltante.tomo != null
                ? tomoFaltante.tomo.getPortadaDataUrl()
                : null);
        if (bitmap != null) {
            imagenPortada.setImageBitmap(bitmap);
        } else {
            imagenPortada.setImageResource(R.drawable.default_profile_picture);
        }
        tarjeta.addView(imagenPortada);

        TextView textoTomo = new TextView(contexto);
        textoTomo.setText(getVolumeLabel(tomoFaltante != null ? tomoFaltante.tomo : null));
        textoTomo.setTextSize(13f);
        textoTomo.setPadding(0, dpToPx(6), 0, 0);
        tarjeta.addView(textoTomo);

        if (mostrarNombreComic) {
            TextView textoComic = new TextView(contexto);
            textoComic.setText(tomoFaltante != null ? String.valueOf(tomoFaltante.comicNombre) : "");
            textoComic.setTextSize(12f);
            textoComic.setMaxLines(2);
            textoComic.setEllipsize(TextUtils.TruncateAt.END);
            tarjeta.addView(textoComic);
        }

        Button botonAbrir = new Button(contexto);
        botonAbrir.setText(R.string.tomos_faltantes_ver_detalle);
        botonAbrir.setOnClickListener(v -> {
            if (listener != null && tomoFaltante != null) {
                listener.onVolumeClick(tomoFaltante);
            }
        });
        tarjeta.addView(botonAbrir);

        imagenPortada.setOnClickListener(v -> {
            if (listener != null && tomoFaltante != null) {
                listener.onVolumeClick(tomoFaltante);
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

    // Convierte dataUrl a bitmap para mostrar portada.
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

    // Convierte dp a pixeles para medir vistas.
    private int dpToPx(int dp) {
        float densidad = contexto.getResources().getDisplayMetrics().density;
        return Math.round(dp * densidad);
    }
}
