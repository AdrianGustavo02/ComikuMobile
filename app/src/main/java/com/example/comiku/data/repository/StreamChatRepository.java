package com.example.comiku.data.repository;

import android.content.Context;
import android.text.TextUtils;
import com.example.comiku.BuildConfig;
import com.example.comiku.data.model.CreateChannelRequest;
import com.example.comiku.data.model.CreateChannelResponse;
import com.example.comiku.data.model.StreamActionResponse;
import com.example.comiku.data.model.StreamChatTokenResponse;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

// Repositorio para manejar integracion con backend y StreamChat
public class StreamChatRepository {
    private static StreamChatRepository instancia;
    private final StreamChatApiService apiService;
    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;
    private final String backendUrl;
    private String streamApiKey;
    private String streamToken;

    // API para operaciones de StreamChat en backend
    public interface StreamChatApiService {
        @POST("/api/stream/token")
        Call<StreamChatTokenResponse> obtenerToken(@Header("Authorization") String bearerToken);

        @POST("/api/stream/channels/create")
        Call<CreateChannelResponse> crearCanal(@Header("Authorization") String bearerToken, @Body CreateChannelRequest request);

        @POST("/api/stream/channels/{channelId}/update")
        Call<StreamActionResponse> actualizarGrupo(@Header("Authorization") String bearerToken, @Path("channelId") String channelId, @Body Map<String, Object> body);

        @POST("/api/stream/channels/{channelId}/add-members")
        Call<StreamActionResponse> agregarMiembros(@Header("Authorization") String bearerToken, @Path("channelId") String channelId, @Body Map<String, Object> body);

        @POST("/api/stream/channels/{channelId}/make-admin")
        Call<StreamActionResponse> hacerAdmin(@Header("Authorization") String bearerToken, @Path("channelId") String channelId, @Body Map<String, Object> body);

        @POST("/api/stream/channels/{channelId}/remove-member")
        Call<StreamActionResponse> quitarMiembro(@Header("Authorization") String bearerToken, @Path("channelId") String channelId, @Body Map<String, Object> body);

        @POST("/api/stream/channels/{channelId}/leave")
        Call<StreamActionResponse> abandonarGrupo(@Header("Authorization") String bearerToken, @Path("channelId") String channelId);

        @DELETE("/api/admin/channels/{channelId}")
        Call<StreamActionResponse> borrarGrupo(@Header("Authorization") String bearerToken, @Path("channelId") String channelId);
    }

    private StreamChatRepository(Context context) {
        this.firestore = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        String backendConfigurado = BuildConfig.BACKEND_URL == null || BuildConfig.BACKEND_URL.trim().isEmpty()
                ? "http://10.0.2.2:3000"
                : BuildConfig.BACKEND_URL.trim();
        if (!backendConfigurado.endsWith("/")) {
            backendConfigurado = backendConfigurado + "/";
        }
        this.backendUrl = backendConfigurado;
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(this.backendUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        this.apiService = retrofit.create(StreamChatApiService.class);
        this.streamApiKey = BuildConfig.STREAM_API_KEY;
    }

    // Obtener singleton del repositorio
    public static synchronized StreamChatRepository obtenerInstancia(Context context) {
        if (instancia == null) {
            instancia = new StreamChatRepository(context);
        }
        return instancia;
    }

    // Obtener token de StreamChat
    public void obtenerTokenStreamChat(TokenCallback callback) {
        withFirebaseToken(new FirebaseTokenCallback() {
            @Override
            public void onToken(String bearerToken) {
                apiService.obtenerToken(bearerToken).enqueue(new Callback<StreamChatTokenResponse>() {
                    @Override
                    public void onResponse(Call<StreamChatTokenResponse> call, Response<StreamChatTokenResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                            StreamChatTokenResponse body = response.body();
                            streamToken = body.getToken();
                            streamApiKey = body.getApiKey();
                            callback.onExito(body);
                            return;
                        }
                        callback.onError("Error en respuesta del servidor");
                    }

                    @Override
                    public void onFailure(Call<StreamChatTokenResponse> call, Throwable t) {
                        callback.onError(construirErrorConexion(t));
                    }
                });
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Crear canal (personal o grupo) por backend
    public void crearCanal(CreateChannelRequest request, ChannelCallback callback) {
        withFirebaseToken(new FirebaseTokenCallback() {
            @Override
            public void onToken(String bearerToken) {
                apiService.crearCanal(bearerToken, request).enqueue(new Callback<CreateChannelResponse>() {
                    @Override
                    public void onResponse(Call<CreateChannelResponse> call, Response<CreateChannelResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().isOk() && response.body().getChannel() != null) {
                            callback.onExito(response.body().getChannel());
                            return;
                        }
                        String mensaje = response.body() != null ? response.body().getMessage() : "Error en respuesta del servidor";
                        callback.onError(mensaje);
                    }

                    @Override
                    public void onFailure(Call<CreateChannelResponse> call, Throwable t) {
                        callback.onError(construirErrorConexion(t));
                    }
                });
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Actualizar datos del grupo por backend
    public void actualizarGrupo(String channelId, String groupName, String groupDescription, String groupImageUrl, ActionCallback callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("groupName", groupName);
        body.put("groupDescription", groupDescription);
        body.put("groupImageUrl", groupImageUrl);
        ejecutarAccion(channelId, body, AccionTipo.ACTUALIZAR_GRUPO, callback);
    }

    // Agregar miembros al grupo por backend
    public void agregarMiembros(String channelId, java.util.List<String> newMemberUids, ActionCallback callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("newMemberUids", newMemberUids);
        ejecutarAccion(channelId, body, AccionTipo.AGREGAR_MIEMBROS, callback);
    }

    // Promover miembro a admin por backend
    public void hacerAdmin(String channelId, String userUid, ActionCallback callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("userUid", userUid);
        ejecutarAccion(channelId, body, AccionTipo.HACER_ADMIN, callback);
    }

    // Quitar miembro del grupo por backend
    public void quitarMiembro(String channelId, String memberUid, ActionCallback callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("memberUid", memberUid);
        ejecutarAccion(channelId, body, AccionTipo.QUITAR_MIEMBRO, callback);
    }

    // Abandonar grupo por backend
    public void abandonarGrupo(String channelId, ActionCallback callback) {
        withFirebaseToken(new FirebaseTokenCallback() {
            @Override
            public void onToken(String bearerToken) {
                apiService.abandonarGrupo(bearerToken, channelId).enqueue(new Callback<StreamActionResponse>() {
                    @Override
                    public void onResponse(Call<StreamActionResponse> call, Response<StreamActionResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                            callback.onExito(response.body());
                            return;
                        }
                        String mensaje = response.body() != null ? response.body().getMessage() : "No fue posible abandonar grupo";
                        callback.onError(mensaje);
                    }

                    @Override
                    public void onFailure(Call<StreamActionResponse> call, Throwable t) {
                        callback.onError(construirErrorConexion(t));
                    }
                });
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Borrar grupo por backend
    public void borrarGrupo(String channelId, ActionCallback callback) {
        withFirebaseToken(new FirebaseTokenCallback() {
            @Override
            public void onToken(String bearerToken) {
                apiService.borrarGrupo(bearerToken, channelId).enqueue(new Callback<StreamActionResponse>() {
                    @Override
                    public void onResponse(Call<StreamActionResponse> call, Response<StreamActionResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                            callback.onExito(response.body());
                            return;
                        }
                        String mensaje = response.body() != null ? response.body().getMessage() : "No fue posible borrar grupo";
                        callback.onError(mensaje);
                    }

                    @Override
                    public void onFailure(Call<StreamActionResponse> call, Throwable t) {
                        callback.onError(construirErrorConexion(t));
                    }
                });
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Ejecutar accion estandar de grupo en backend
    private void ejecutarAccion(String channelId, Map<String, Object> body, AccionTipo accionTipo, ActionCallback callback) {
        withFirebaseToken(new FirebaseTokenCallback() {
            @Override
            public void onToken(String bearerToken) {
                Call<StreamActionResponse> call;
                switch (accionTipo) {
                    case ACTUALIZAR_GRUPO:
                        call = apiService.actualizarGrupo(bearerToken, channelId, body);
                        break;
                    case AGREGAR_MIEMBROS:
                        call = apiService.agregarMiembros(bearerToken, channelId, body);
                        break;
                    case HACER_ADMIN:
                        call = apiService.hacerAdmin(bearerToken, channelId, body);
                        break;
                    case QUITAR_MIEMBRO:
                        call = apiService.quitarMiembro(bearerToken, channelId, body);
                        break;
                    default:
                        callback.onError("Accion no soportada");
                        return;
                }

                call.enqueue(new Callback<StreamActionResponse>() {
                    @Override
                    public void onResponse(Call<StreamActionResponse> call, Response<StreamActionResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                            callback.onExito(response.body());
                            return;
                        }
                        String mensaje = response.body() != null ? response.body().getMessage() : "No fue posible completar la accion";
                        callback.onError(mensaje);
                    }

                    @Override
                    public void onFailure(Call<StreamActionResponse> call, Throwable t) {
                        callback.onError(construirErrorConexion(t));
                    }
                });
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Resolver token firebase para llamadas al backend
    private void withFirebaseToken(FirebaseTokenCallback callback) {
        if (auth.getCurrentUser() == null) {
            callback.onError("No hay usuario autenticado");
            return;
        }

        auth.getCurrentUser().getIdToken(true).addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                callback.onError("Error al obtener token");
                return;
            }
            callback.onToken("Bearer " + task.getResult().getToken());
        });
    }

    public interface FirebaseTokenCallback {
        void onToken(String bearerToken);

        void onError(String error);
    }

    public enum AccionTipo {
        ACTUALIZAR_GRUPO,
        AGREGAR_MIEMBROS,
        HACER_ADMIN,
        QUITAR_MIEMBRO
    }

    // Callbacks publicos
    public interface TokenCallback {
        void onExito(StreamChatTokenResponse response);

        void onError(String error);
    }

    public interface ChannelCallback {
        void onExito(CreateChannelResponse.ChannelInfo channel);

        void onError(String error);
    }

    public interface ActionCallback {
        void onExito(StreamActionResponse response);

        void onError(String error);
    }

    public String obtenerStreamApiKey() {
        return streamApiKey;
    }

    public String obtenerStreamToken() {
        return streamToken;
    }

    // Construye un error de red mas claro para poder configurar bien el backend.
    private String construirErrorConexion(Throwable error) {
        String mensajeBase = error != null && error.getMessage() != null
                ? error.getMessage()
                : "Error de red desconocido";
        String claseError = error != null ? error.getClass().getSimpleName() : "SinDetalle";
        StringBuilder mensaje = new StringBuilder();
        mensaje.append("Fallo de conexión: ")
                .append(mensajeBase)
                .append(" (")
                .append(claseError)
                .append("). URL backend: ")
                .append(backendUrl);

        if (!TextUtils.isEmpty(mensajeBase) && mensajeBase.toUpperCase().contains("CLEARTEXT")) {
            mensaje.append(". El backend debe permitir HTTP o usar HTTPS valido.");
        }

        if (backendUrl.contains("10.0.2.2")) {
            mensaje.append(". Si usas un dispositivo fisico, configura BACKEND_URL con la IP LAN del backend");
        }

        return mensaje.toString();
    }
}
