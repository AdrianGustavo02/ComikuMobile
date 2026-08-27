package com.example.comiku.screens;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import com.example.comiku.data.model.VolumeDetailData;

import java.util.List;

public class VolumePortadaAdapter extends BaseAdapter {
    private final Context contexto;
    private final List<VolumeDetailData> tomos;
    private final OnVolumeClickListener listener;

    public interface OnVolumeClickListener {
        void onVolumeClick(VolumeDetailData tomo);
    }

    // Crea el adaptador para mostrar portadas de tomos en grid.
    public VolumePortadaAdapter(
            Context contexto,
            List<VolumeDetailData> tomos,
            OnVolumeClickListener listener
    ) {
        this.contexto = contexto;
        this.tomos = tomos;
        this.listener = listener;
    }

    @Override
    public int getCount() {
        return tomos.size();
    }

    @Override
    public Object getItem(int position) {
        return tomos.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ImageView imagenPortada;

        if (convertView == null) {
            imagenPortada = new ImageView(contexto);
            imagenPortada.setLayoutParams(new android.widget.GridView.LayoutParams(200, 280));
            imagenPortada.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            imagenPortada = (ImageView) convertView;
        }

        VolumeDetailData tomo = tomos.get(position);
        String dataUrlPortada = tomo.getPortadaDataUrl();

        if (!TextUtils.isEmpty(dataUrlPortada)) {
            Bitmap bitmap = decodeDataUrl(dataUrlPortada);
            if (bitmap != null) {
                imagenPortada.setImageBitmap(bitmap);
            }
        }

        imagenPortada.setOnClickListener(v -> {
            if (listener != null) {
                listener.onVolumeClick(tomo);
            }
        });

        return imagenPortada;
    }

    // Convierte dataUrl a bitmap.
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
}
