package com.example.comiku.ui.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.Toast;

import com.example.comiku.data.model.places.NearbyBookstoreDto;
import com.example.comiku.R;
import com.example.comiku.core.ui.ToastUtils;

import java.util.Locale;

public class MapsHelper {
    private static final String PAQUETE_GOOGLE_MAPS = "com.google.android.apps.maps";

    private MapsHelper() {
    }

    // Abre un local en Google Maps.
    public static void openPlace(Context context, NearbyBookstoreDto place) {
        if (context == null || place == null) {
            return;
        }

        String urlPrincipal = place.getGoogleMapsUrl();
        String urlRespaldo = new Uri.Builder()
                .scheme("https")
                .authority("www.google.com")
                .path("maps/search/")
                .appendQueryParameter("api", "1")
                .appendQueryParameter("query", place.getLatitude() + "," + place.getLongitude())
                .appendQueryParameter("query_place_id", place.getId())
                .build()
                .toString();

        if (tryOpenUrl(context, urlPrincipal) || tryOpenUrl(context, urlRespaldo)) {
            return;
        }

        ToastUtils.showTextToast(context, R.string.volume_detail_error_abrir_mapa, Toast.LENGTH_SHORT);
    }

    // Abre las indicaciones para llegar a un local en Google Maps.
    public static void openDirections(Context context, NearbyBookstoreDto place) {
        if (context == null || place == null) {
            return;
        }

        String url = new Uri.Builder()
                .scheme("https")
                .authority("www.google.com")
                .path("maps/dir/")
                .appendQueryParameter("api", "1")
                .appendQueryParameter("destination", place.getLatitude() + "," + place.getLongitude())
                .appendQueryParameter("destination_place_id", place.getId())
                .build()
                .toString();

        if (tryOpenUrl(context, url)) {
            return;
        }

        ToastUtils.showTextToast(context, R.string.volume_detail_error_abrir_mapa, Toast.LENGTH_SHORT);
    }

    // Intenta abrir una URL primero con Google Maps y luego con cualquier app compatible.
    private static boolean tryOpenUrl(Context context, String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }

        Uri uri = Uri.parse(url);
        Intent intentMaps = new Intent(Intent.ACTION_VIEW, uri);
        intentMaps.setPackage(PAQUETE_GOOGLE_MAPS);

        try {
            context.startActivity(intentMaps);
            return true;
        } catch (ActivityNotFoundException | SecurityException error) {
            // Si Google Maps no puede abrirlo, pruebo con otra app compatible.
        }

        Intent intentGenerico = new Intent(Intent.ACTION_VIEW, uri);
        try {
            context.startActivity(intentGenerico);
            return true;
        } catch (ActivityNotFoundException | SecurityException error) {
            return false;
        }
    }

    // Formatea una distancia en metros a formato legible (m o km).
    public static String formatDistance(int distanceMeters) {
        if (distanceMeters < 1_000) {
            return distanceMeters + " m";
        } else {
            return String.format(Locale.forLanguageTag("es-AR"), "%.1f km", distanceMeters / 1_000.0);
        }
    }
}
