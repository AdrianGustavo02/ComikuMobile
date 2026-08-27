package com.example.comiku.core.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public final class ThematicListUiHelper {

    private ThematicListUiHelper() {
    }

    // Crea un contenedor base para una tarjeta simple.
    public static LinearLayout createCardContainer(Context contexto) {
        LinearLayout tarjeta = new LinearLayout(contexto);
        tarjeta.setOrientation(LinearLayout.VERTICAL);
        tarjeta.setPadding(dpToPx(contexto, 12), dpToPx(contexto, 12), dpToPx(contexto, 12), dpToPx(contexto, 12));
        tarjeta.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dpToPx(contexto, 12);
        tarjeta.setLayoutParams(params);
        return tarjeta;
    }

    // Crea un titulo reutilizable para tarjetas y bloques.
    public static TextView createTitle(Context contexto, String texto, float tamanoSp) {
        TextView vistaTexto = new TextView(contexto);
        vistaTexto.setText(texto);
        vistaTexto.setTextSize(tamanoSp);
        vistaTexto.setTypeface(null, Typeface.BOLD);
        return vistaTexto;
    }

    // Crea una fila horizontal de portadas o un mensaje vacio.
    public static LinearLayout createWallpaperStrip(Context contexto, List<String> fotos, int altoDp, String textoVacio) {
        LinearLayout wallpaper = new LinearLayout(contexto);
        wallpaper.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(contexto, altoDp)
        );
        params.bottomMargin = dpToPx(contexto, 10);
        wallpaper.setLayoutParams(params);

        if (fotos == null || fotos.isEmpty()) {
            TextView texto = new TextView(contexto);
            texto.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            texto.setGravity(Gravity.CENTER);
            texto.setText(textoVacio);
            wallpaper.addView(texto);
            return wallpaper;
        }

        int limite = Math.min(3, fotos.size());
        for (int i = 0; i < limite; i++) {
            ImageView imagen = new ImageView(contexto);
            LinearLayout.LayoutParams paramsImagen = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
            );
            if (i > 0) {
                paramsImagen.leftMargin = dpToPx(contexto, 4);
            }
            imagen.setLayoutParams(paramsImagen);
            imagen.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap bitmap = decodeDataUrl(fotos.get(i));
            if (bitmap != null) {
                imagen.setImageBitmap(bitmap);
            }
            wallpaper.addView(imagen);
        }
        return wallpaper;
    }

    // Crea una mini portada para listas de tomos.
    public static ImageView createMiniCover(Context contexto, String dataUrl, int anchoDp, int altoDp) {
        ImageView imagen = new ImageView(contexto);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dpToPx(contexto, anchoDp),
                dpToPx(contexto, altoDp)
        );
        params.rightMargin = dpToPx(contexto, 8);
        imagen.setLayoutParams(params);
        imagen.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap bitmap = decodeDataUrl(dataUrl);
        if (bitmap != null) {
            imagen.setImageBitmap(bitmap);
        }
        return imagen;
    }

    // Convierte una cadena dataUrl en un bitmap.
    public static Bitmap decodeDataUrl(String dataUrl) {
        if (TextUtils.isEmpty(dataUrl)) {
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
    public static int dpToPx(Context contexto, int dp) {
        float densidad = contexto.getResources().getDisplayMetrics().density;
        return Math.round(dp * densidad);
    }
}
