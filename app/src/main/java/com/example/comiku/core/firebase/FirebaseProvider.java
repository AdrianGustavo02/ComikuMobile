package com.example.comiku.core.firebase;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.example.comiku.BuildConfig;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

public final class FirebaseProvider {
    private static final String ETIQUETA_LOG = "FirebaseProvider";
    private static final String SUFIJO_BUCKET_POR_DEFECTO = ".appspot.com";

    private FirebaseProvider() {
    }

    // Inicia Firebase con la configuracion del proyecto.
    public static void initialize(Context contexto) {
        if (!isConfigured()) {
            Log.w(ETIQUETA_LOG, "Firebase no esta configurado. Revisa gradle.properties.");
            return;
        }

        if (FirebaseApp.getApps(contexto).isEmpty()) {
            FirebaseApp.initializeApp(contexto, buildOptions());
        }
    }

    // Valida que existan los datos para abrir la conexion.
    public static boolean isConfigured() {
        return !TextUtils.isEmpty(BuildConfig.FIREBASE_API_KEY)
                && !TextUtils.isEmpty(BuildConfig.FIREBASE_APP_ID)
                && !TextUtils.isEmpty(BuildConfig.FIREBASE_PROJECT_ID);
    }

    // Crea las opciones de Firebase a partir de BuildConfig.
    private static FirebaseOptions buildOptions() {
        String bucketNormalizado = normalizeStorageBucket(
                BuildConfig.FIREBASE_STORAGE_BUCKET,
                BuildConfig.FIREBASE_PROJECT_ID
        );

        return new FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FIREBASE_MESSAGING_SENDER_ID)
                .setStorageBucket(bucketNormalizado)
                .build();
    }


    private static String normalizeStorageBucket(String bucketCrudo, String projectId) {
        if (TextUtils.isEmpty(bucketCrudo)) {
            if (TextUtils.isEmpty(projectId)) {
                return "";
            }

            return projectId + SUFIJO_BUCKET_POR_DEFECTO;
        }

        String bucketNormalizado = bucketCrudo.trim();
        bucketNormalizado = bucketNormalizado.replaceFirst("^gs://", "");
        bucketNormalizado = bucketNormalizado.replaceFirst("^https?://[^/]+/v0/b/", "");
        bucketNormalizado = bucketNormalizado.replaceFirst("/.*$", "");
        return bucketNormalizado;
    }
}
