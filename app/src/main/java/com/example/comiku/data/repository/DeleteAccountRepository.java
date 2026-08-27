package com.example.comiku.data.repository;

import android.text.TextUtils;

import com.example.comiku.BuildConfig;
import com.example.comiku.data.model.StreamActionResponse;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.DELETE;
import retrofit2.http.Header;

public final class DeleteAccountRepository {
    private static final String MENSAJE_ERROR_DESCONOCIDO = "Ocurrio un error al eliminar tu cuenta. Intenta nuevamente.";
    private static final String MENSAJE_ERROR_SIN_SESION = "No hay usuario autenticado.";

    private interface DeleteAccountApiService {
        @DELETE("/api/users/me")
        Call<StreamActionResponse> deleteOwnAccount(@Header("Authorization") String bearerToken);
    }

    private static DeleteAccountApiService servicioApi;

    private DeleteAccountRepository() {
    }

    // Elimina la cuenta propia usando el endpoint backend de referencia React.
    public static Task<String> deleteAccountComplete() {
        TaskCompletionSource<String> fuenteResultado = new TaskCompletionSource<>();
        FirebaseUser usuarioActual = FirebaseAuth.getInstance().getCurrentUser();
        if (usuarioActual == null) {
            fuenteResultado.setException(new IllegalStateException(MENSAJE_ERROR_SIN_SESION));
            return fuenteResultado.getTask();
        }

        usuarioActual.getIdToken(true)
                .addOnSuccessListener(tokenResult -> {
                    String token = tokenResult != null ? tokenResult.getToken() : "";
                    if (TextUtils.isEmpty(token)) {
                        fuenteResultado.setException(new IllegalStateException(MENSAJE_ERROR_SIN_SESION));
                        return;
                    }
                    ejecutarEliminacion("Bearer " + token, fuenteResultado);
                })
                .addOnFailureListener(fuenteResultado::setException);

        return fuenteResultado.getTask();
    }

    // Ejecuta la llamada al backend y devuelve el mensaje de respuesta.
    private static void ejecutarEliminacion(
            String tokenBearer,
            TaskCompletionSource<String> fuenteResultado
    ) {
        obtenerServicioApi().deleteOwnAccount(tokenBearer).enqueue(new Callback<StreamActionResponse>() {
            @Override
            public void onResponse(Call<StreamActionResponse> call, Response<StreamActionResponse> response) {
                StreamActionResponse cuerpo = response.body();
                if (response.isSuccessful() && cuerpo != null && cuerpo.isOk()) {
                    String mensaje = cuerpo.getMessage();
                    fuenteResultado.setResult(TextUtils.isEmpty(mensaje)
                            ? "Cuenta eliminada exitosamente."
                            : mensaje);
                    return;
                }
                String mensajeError = (cuerpo != null && !TextUtils.isEmpty(cuerpo.getMessage()))
                        ? cuerpo.getMessage()
                        : MENSAJE_ERROR_DESCONOCIDO;
                fuenteResultado.setException(new IllegalStateException(mensajeError));
            }

            @Override
            public void onFailure(Call<StreamActionResponse> call, Throwable error) {
                if (error == null || TextUtils.isEmpty(error.getMessage())) {
                    fuenteResultado.setException(new IllegalStateException(MENSAJE_ERROR_DESCONOCIDO));
                    return;
                }
                fuenteResultado.setException(new IllegalStateException(error.getMessage()));
            }
        });
    }

    // Crea el cliente retrofit para consumir backend.
    private static synchronized DeleteAccountApiService obtenerServicioApi() {
        if (servicioApi != null) {
            return servicioApi;
        }

        String backendConfigurado = BuildConfig.BACKEND_URL == null || BuildConfig.BACKEND_URL.trim().isEmpty()
                ? "http://10.0.2.2:3000"
                : BuildConfig.BACKEND_URL.trim();
        if (!backendConfigurado.endsWith("/")) {
            backendConfigurado = backendConfigurado + "/";
        }

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(backendConfigurado)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        servicioApi = retrofit.create(DeleteAccountApiService.class);
        return servicioApi;
    }
}
