package com.example.comiku.data.repository;

import android.text.TextUtils;

import com.example.comiku.data.model.UserSearchData;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class UserSearchRepository {
    private static final String COLECCION_USUARIO = "usuario";
    private static final String SUBCOLECCION_AMIGOS = "Amigos";

    private UserSearchRepository() {
    }

    // Busca usuarios por nick con coincidencia parcial y sin distinguir mayusculas.
    public static Task<List<UserSearchData>> searchUsersByNick(String uidActual, String termino) {
        if (TextUtils.isEmpty(termino)) {
            return Tasks.forResult(new ArrayList<>());
        }

        String terminoNormalizado = termino.trim().toLowerCase(Locale.ROOT);
        if (TextUtils.isEmpty(terminoNormalizado)) {
            return Tasks.forResult(new ArrayList<>());
        }

        return FirebaseFirestore.getInstance()
                .collection(COLECCION_USUARIO)
                .limit(500)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw getTaskError(task, "No se pudo buscar usuarios");
                    }

                    List<UserSearchData> resultados = new ArrayList<>();
                    for (DocumentSnapshot documento : task.getResult().getDocuments()) {
                        String uid = getUserIdFromData(documento);
                        if (TextUtils.isEmpty(uid) || uid.equals(uidActual)) {
                            continue;
                        }

                        String nick = safeString(documento.getString("Nick"));
                        if (!nick.toLowerCase(Locale.ROOT).contains(terminoNormalizado)) {
                            continue;
                        }

                        resultados.add(buildUserData(documento, uid));
                    }

                    resultados.sort((usuarioA, usuarioB) ->
                            usuarioA.nick.compareToIgnoreCase(usuarioB.nick));
                    return resultados;
                });
    }

    // Obtiene amigos del usuario y sus datos para mostrarlos en pantalla.
    public static Task<List<UserSearchData>> getUserFriends(String uidActual) {
        if (TextUtils.isEmpty(uidActual)) {
            return Tasks.forResult(new ArrayList<>());
        }

        return FirebaseFirestore.getInstance()
                .collection(COLECCION_USUARIO)
                .document(uidActual)
                .collection(SUBCOLECCION_AMIGOS)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw getTaskError(task, "No se pudo obtener la lista de amigos");
                    }

                    List<Task<DocumentSnapshot>> tareas = new ArrayList<>();
                    for (DocumentSnapshot amigoDocumento : task.getResult().getDocuments()) {
                        String uidAmigo = getUserIdFromData(amigoDocumento);
                        if (TextUtils.isEmpty(uidAmigo)) {
                            uidAmigo = amigoDocumento.getId();
                        }
                        if (!TextUtils.isEmpty(uidAmigo)) {
                            tareas.add(FirebaseFirestore.getInstance()
                                    .collection(COLECCION_USUARIO)
                                    .document(uidAmigo)
                                    .get());
                        }
                    }

                    if (tareas.isEmpty()) {
                        return Tasks.forResult(new ArrayList<>());
                    }

                    return Tasks.whenAllSuccess(tareas).continueWith(taskPerfiles -> {
                        if (!taskPerfiles.isSuccessful() || taskPerfiles.getResult() == null) {
                            throw getTaskError(taskPerfiles, "No se pudieron cargar los perfiles de amigos");
                        }

                        List<UserSearchData> amigos = new ArrayList<>();
                        for (Object elemento : taskPerfiles.getResult()) {
                            if (!(elemento instanceof DocumentSnapshot)) {
                                continue;
                            }
                            DocumentSnapshot documento = (DocumentSnapshot) elemento;
                            if (!documento.exists()) {
                                continue;
                            }
                            amigos.add(buildUserData(documento, documento.getId()));
                        }

                        amigos.sort((usuarioA, usuarioB) ->
                                usuarioA.nick.compareToIgnoreCase(usuarioB.nick));
                        return amigos;
                    });
                });
    }

    // Convierte un documento de usuario en modelo para la UI.
    private static UserSearchData buildUserData(DocumentSnapshot documento, String uid) {
        String nombre = safeString(documento.getString("Nombre"));
        String apellido = safeString(documento.getString("Apellido"));
        String nombreCompleto = (nombre + " " + apellido).trim();

        return new UserSearchData(
                uid,
                safeString(documento.getString("Nick")),
                nombreCompleto,
                extractPhotoDataUrl(documento.get("FotoPerfil"))
        );
    }

    // Obtiene el uid priorizando UserID y usando id de documento como respaldo.
    private static String getUserIdFromData(DocumentSnapshot documento) {
        if (documento == null) {
            return "";
        }
        Object userId = documento.get("UserID");
        if (userId != null) {
            String uid = String.valueOf(userId).trim();
            if (!TextUtils.isEmpty(uid)) {
                return uid;
            }
        }
        return documento.getId();
    }

    // Extrae dataUrl de FotoPerfil para mostrar la imagen.
    private static String extractPhotoDataUrl(Object fotoPerfil) {
        if (!(fotoPerfil instanceof Map)) {
            return "";
        }
        Map<?, ?> mapaFoto = (Map<?, ?>) fotoPerfil;
        Object dataUrl = mapaFoto.get("dataUrl");
        return dataUrl == null ? "" : String.valueOf(dataUrl);
    }

    // Devuelve un string seguro evitando valores nulos.
    private static String safeString(String valor) {
        return valor == null ? "" : valor;
    }

    // Devuelve una excepcion clara para tareas fallidas.
    private static Exception getTaskError(Task<?> tarea, String mensaje) {
        Exception error = tarea.getException();
        return error != null ? error : new Exception(mensaje);
    }
}

