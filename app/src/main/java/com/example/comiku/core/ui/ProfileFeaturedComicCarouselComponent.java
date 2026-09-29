package com.example.comiku.core.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.comiku.R;
import com.example.comiku.data.model.UserShelfComicGroupData;
import com.example.comiku.data.model.UserShelfVolumeItemData;

import java.util.Collections;
import java.util.List;
import java.util.Set;

// Renderiza el carrusel de comics destacados del perfil.
public final class ProfileFeaturedComicCarouselComponent {
    private final Context contexto;

    public interface OnComicClickListener {
        void onComicClick(UserShelfComicGroupData item);
    }

    public ProfileFeaturedComicCarouselComponent(Context contexto) {
        this.contexto = contexto;
    }

    // Dibuja las cards del carrusel segun el modo actual.
    public void renderComics(
            LinearLayout contenedor,
            List<UserShelfComicGroupData> comics,
            boolean modoEdicion,
            Set<String> comicsSeleccionados,
            OnComicClickListener listener
    ) {
        contenedor.removeAllViews();
        if (comics == null || comics.isEmpty()) {
            return;
        }

        Set<String> seleccionados = comicsSeleccionados == null ? Collections.emptySet() : comicsSeleccionados;
        for (UserShelfComicGroupData item : comics) {
            contenedor.addView(createComicCard(item, modoEdicion, seleccionados.contains(getComicId(item)), listener));
        }
    }

    // Crea una card simple con portada, nombre y contador de tomos.
    private View createComicCard(
            UserShelfComicGroupData item,
            boolean modoEdicion,
            boolean seleccionado,
            OnComicClickListener listener
    ) {
        LinearLayout tarjeta = new LinearLayout(contexto);
        tarjeta.setOrientation(LinearLayout.VERTICAL);
        tarjeta.setBackgroundResource(seleccionado
                ? R.drawable.bg_volume_cover_card_pressed
                : R.drawable.bg_volume_cover_card);
        LinearLayout.LayoutParams paramsTarjeta = new LinearLayout.LayoutParams(
                dpToPx(160),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsTarjeta.setMarginEnd(dpToPx(12));
        tarjeta.setLayoutParams(paramsTarjeta);
        tarjeta.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));
        tarjeta.setFocusable(true);
        tarjeta.setClickable(true);

        ImageView portada = new ImageView(contexto);
        LinearLayout.LayoutParams paramsPortada = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(200)
        );
        portada.setLayoutParams(paramsPortada);
        portada.setScaleType(ImageView.ScaleType.CENTER_CROP);
        portada.setBackgroundResource(R.drawable.bg_volume_cover_image);
        portada.setClipToOutline(true);
        portada.setContentDescription(contexto.getString(R.string.portada_del_tomo));

        Bitmap bitmap = ThematicListUiHelper.decodeDataUrl(getFeaturedCoverDataUrl(item));
        if (bitmap != null) {
            portada.setImageBitmap(bitmap);
        } else {
            portada.setImageResource(R.drawable.bg_volume_cover_card_default);
        }
        tarjeta.addView(portada);

        TextView textoNombre = new TextView(contexto);
        LinearLayout.LayoutParams paramsNombre = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsNombre.topMargin = dpToPx(10);
        textoNombre.setLayoutParams(paramsNombre);
        textoNombre.setText(getComicName(item));
        textoNombre.setTextSize(12f);
        textoNombre.setTypeface(null, android.graphics.Typeface.BOLD);
        textoNombre.setTextColor(contexto.getColor(android.R.color.white));
        textoNombre.setMaxLines(3);
        textoNombre.setEllipsize(TextUtils.TruncateAt.END);
        tarjeta.addView(textoNombre);

        TextView textoTomos = new TextView(contexto);
        LinearLayout.LayoutParams paramsTomos = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsTomos.topMargin = dpToPx(4);
        textoTomos.setLayoutParams(paramsTomos);
        textoTomos.setText(contexto.getString(R.string.perfil_destacados_tomos_guardados, getVolumesCount(item)));
        textoTomos.setTextSize(11f);
        textoTomos.setTextColor(contexto.getColor(R.color.carousel_subtitle));
        tarjeta.addView(textoTomos);

        if (modoEdicion) {
            TextView textoEstado = new TextView(contexto);
            LinearLayout.LayoutParams paramsEstado = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            paramsEstado.topMargin = dpToPx(4);
            textoEstado.setLayoutParams(paramsEstado);
            textoEstado.setText(seleccionado
                    ? contexto.getString(R.string.perfil_destacados_estado_destacado)
                    : contexto.getString(R.string.perfil_destacados_estado_disponible));
            textoEstado.setTextSize(10f);
            textoEstado.setTextColor(contexto.getColor(R.color.carousel_subtitle));
            tarjeta.addView(textoEstado);
        }

        tarjeta.setOnClickListener(v -> {
            if (listener != null) {
                listener.onComicClick(item);
            }
        });
        return tarjeta;
    }

    // Devuelve el id del comic de un grupo.
    private String getComicId(UserShelfComicGroupData item) {
        return item == null || TextUtils.isEmpty(item.comicId) ? "" : item.comicId;
    }

    // Obtiene el nombre a mostrar del comic.
    private String getComicName(UserShelfComicGroupData item) {
        if (item == null || item.comic == null || TextUtils.isEmpty(item.comic.nombre)) {
            return "";
        }
        return item.comic.nombre;
    }

    // Obtiene cuántos tomos guarda el usuario de ese comic.
    private int getVolumesCount(UserShelfComicGroupData item) {
        return item == null || item.tomos == null ? 0 : item.tomos.size();
    }

    // Busca la portada representativa del comic.
    private String getFeaturedCoverDataUrl(UserShelfComicGroupData item) {
        if (item == null) {
            return null;
        }
        UserShelfVolumeItemData featuredVolume = item.getFeaturedVolume();
        if (featuredVolume == null || featuredVolume.tomo == null) {
            return null;
        }
        return featuredVolume.tomo.getPortadaDataUrl();
    }

    // Convierte dp a pixeles.
    private int dpToPx(int dp) {
        float densidad = contexto.getResources().getDisplayMetrics().density;
        return Math.round(dp * densidad);
    }
}
