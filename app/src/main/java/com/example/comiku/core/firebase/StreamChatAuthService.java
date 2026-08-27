package com.example.comiku.core.firebase;

import android.content.Context;
import android.util.Log;
import com.example.comiku.data.model.StreamChatTokenResponse;
import com.example.comiku.data.repository.StreamChatRepository;
import com.google.firebase.auth.FirebaseAuth;
import java.util.ArrayList;
import java.util.List;

public class StreamChatAuthService {
    private static final String TAG = "StreamChatAuthService";
    private static StreamChatAuthService instancia;
    private final Context context;
    private final StreamChatRepository repositorio;
    private final List<AuthListener> listeners;
    private boolean isAuthenticated = false;
    private String streamToken;
    private String streamApiKey;

    public interface AuthListener {
        void onAuthSuccess(String token, String apiKey);
        void onAuthFailure(String error);
    }

    private StreamChatAuthService(Context context) {
        this.context = context;
        this.repositorio = StreamChatRepository.obtenerInstancia(context);
        this.listeners = new ArrayList<>();
    }

    // Obtener instancia singleton
    public static synchronized StreamChatAuthService obtenerInstancia(Context context) {
        if (instancia == null) {
            instancia = new StreamChatAuthService(context);
        }
        return instancia;
    }

    // Registrar un listener para cambios de autenticación
    public void registrarListener(AuthListener listener) {
        listeners.add(listener);
    }

    // Desregistrar un listener
    public void desregistrarListener(AuthListener listener) {
        listeners.remove(listener);
    }

    // Autenticar con StreamChat
    public void autenticar(AuthCallback callback) {
        // Verificar si el usuario ya está autenticado
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        if (firebaseAuth.getCurrentUser() == null) {
            callback.onError("Usuario no autenticado en Firebase");
            return;
        }

        // Obtener el token de Stream Chat del backend
        repositorio.obtenerTokenStreamChat(new StreamChatRepository.TokenCallback() {
            @Override
            public void onExito(StreamChatTokenResponse response) {
                streamToken = response.getToken();
                streamApiKey = response.getApiKey();
                isAuthenticated = true;

                Log.d(TAG, "Autenticación con Stream Chat exitosa");
                callback.onExito(response);
                notificarListenersExito(streamToken, streamApiKey);
            }

            @Override
            public void onError(String error) {
                isAuthenticated = false;
                Log.e(TAG, "Error en autenticación: " + error);
                callback.onError(error);
                notificarListenersError(error);
            }
        });
    }

    // Verificar si el usuario está autenticado
    public boolean estaAutenticado() {
        return isAuthenticated && streamToken != null;
    }

    // Obtener el token de Stream Chat
    public String obtenerStreamToken() {
        return streamToken;
    }

    // Obtener el API Key de Stream Chat
    public String obtenerStreamApiKey() {
        return streamApiKey;
    }

    // Cerrar sesión
    public void cerrarSesion() {
        isAuthenticated = false;
        streamToken = null;
        streamApiKey = null;
    }

    // Notificar a los listeners de éxito
    private void notificarListenersExito(String token, String apiKey) {
        for (AuthListener listener : listeners) {
            listener.onAuthSuccess(token, apiKey);
        }
    }

    // Notificar a los listeners de error
    private void notificarListenersError(String error) {
        for (AuthListener listener : listeners) {
            listener.onAuthFailure(error);
        }
    }

    // Interface para callbacks
    public interface AuthCallback {
        void onExito(StreamChatTokenResponse response);
        void onError(String error);
    }
}
