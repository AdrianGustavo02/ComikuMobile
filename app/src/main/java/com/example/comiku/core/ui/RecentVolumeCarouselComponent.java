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

import com.example.comiku.R;
import com.example.comiku.data.model.RecentLibraryVolumeData;

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
        ImageView imagenPortada = new ImageView(contexto);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dpToPx(150), dpToPx(210));
        params.setMarginEnd(dpToPx(10));
        imagenPortada.setLayoutParams(params);
        imagenPortada.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imagenPortada.setContentDescription(contexto.getString(R.string.portada_del_tomo));

        Bitmap bitmap = decodeDataUrl(tomoReciente != null && tomoReciente.tomo != null
                ? tomoReciente.tomo.getPortadaDataUrl()
                : null);
        if (bitmap != null) {
            imagenPortada.setImageBitmap(bitmap);
        } else {
            imagenPortada.setImageResource(R.drawable.default_profile_picture);
        }

        imagenPortada.setOnClickListener(v -> {
            if (listener != null && tomoReciente != null) {
                listener.onVolumeClick(tomoReciente);
            }
        });
        return imagenPortada;
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
