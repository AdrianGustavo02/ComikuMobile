package com.example.comiku.core.firebase;

import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class PrivacyService {
    private static final String TAG = "PrivacyService";
    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;

    public interface PrivacyCheckCallback {
        void onExito();
        void onError(String razon);
    }

    public enum RazonBloqueo {
        USUARIO_NO_EXISTE,           // El usuario no existe en Firestore
        USUARIO_BLOQUEADO_POR_MI,    // Lo tengo bloqueado
        ME_TIENE_BLOQUEADO,          // El usuario me tiene bloqueado
        USUARIOS_VALIDOS             // Ambos OK
    }

    public PrivacyService() {
        this.firestore = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    // Validar que podemos chatear con este usuario
    public void validarPuedoChateaR(String otroUsuarioId, PrivacyCheckCallback callback) {
        String usuarioActualId = auth.getCurrentUser().getUid();

        // Verificar que el otro usuario existe
        firestore.collection("usuario")
                .document(otroUsuarioId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onError("Usuario no encontrado.");
                        return;
                    }

                    // Verificar que no lo tengo bloqueado
                    verificarBloqueoPorMi(usuarioActualId, otroUsuarioId, callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error verificando usuario: " + e.getMessage());
                    callback.onError("Error al verificar usuario. Intenta de nuevo.");
                });
    }

    // Verificar si yo bloquee al otro usuario
    private void verificarBloqueoPorMi(String usuarioActualId, String otroUsuarioId, 
                                       PrivacyCheckCallback callback) {
        firestore.collection("usuario")
                .document(usuarioActualId)
                .collection("UsuariosBloqueados")
                .document(otroUsuarioId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        callback.onError("No puedes chatear con un usuario que has bloqueado. Desbloquealo primero.");
                        return;
                    }

                    // Verificar que el otro usuario no me tiene bloqueado
                    verificarBloqueoPorOtro(usuarioActualId, otroUsuarioId, callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error verificando bloqueo propio: " + e.getMessage());
                    callback.onError("Error de privacidad. Intenta de nuevo.");
                });
    }

    // Verificar si el otro usuario me bloqueo
    private void verificarBloqueoPorOtro(String usuarioActualId, String otroUsuarioId, 
                                        PrivacyCheckCallback callback) {
        firestore.collection("usuario")
                .document(otroUsuarioId)
                .collection("UsuariosBloqueados")
                .document(usuarioActualId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        callback.onError("Este usuario te tiene bloqueado.");
                        return;
                    }

                    callback.onExito();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error verificando bloqueo del otro: " + e.getMessage());
                    callback.onError("Error de privacidad. Intenta de nuevo.");
                });
    }

    // Obtener razon de por que no puede chatear
    public void obtenerRazonBloqueo(String otroUsuarioId, RazonBloqueoCallback callback) {
        String usuarioActualId = auth.getCurrentUser().getUid();

        firestore.collection("usuario")
                .document(otroUsuarioId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onRazon(RazonBloqueo.USUARIO_NO_EXISTE);
                        return;
                    }

                    // Verificar si yo lo tengo bloqueado
                    firestore.collection("usuario")
                            .document(usuarioActualId)
                            .collection("UsuariosBloqueados")
                            .document(otroUsuarioId)
                            .get()
                            .addOnSuccessListener(doc2 -> {
                                if (doc2.exists()) {
                                    callback.onRazon(RazonBloqueo.USUARIO_BLOQUEADO_POR_MI);
                                    return;
                                }

                                // Verificar si el otro usuario me tiene bloqueado
                                firestore.collection("usuario")
                                        .document(otroUsuarioId)
                                        .collection("UsuariosBloqueados")
                                        .document(usuarioActualId)
                                        .get()
                                        .addOnSuccessListener(doc3 -> {
                                            if (doc3.exists()) {
                                                callback.onRazon(RazonBloqueo.ME_TIENE_BLOQUEADO);
                                            } else {
                                                callback.onRazon(RazonBloqueo.USUARIOS_VALIDOS);
                                            }
                                        })
                                        .addOnFailureListener(e -> callback.onRazon(RazonBloqueo.USUARIOS_VALIDOS));
                            })
                            .addOnFailureListener(e -> callback.onRazon(RazonBloqueo.USUARIOS_VALIDOS));
                })
                .addOnFailureListener(e -> callback.onRazon(RazonBloqueo.USUARIO_NO_EXISTE));
    }

    public interface RazonBloqueoCallback {
        void onRazon(RazonBloqueo razon);
    }
}
