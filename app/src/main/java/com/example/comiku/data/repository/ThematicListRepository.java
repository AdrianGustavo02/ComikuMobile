package com.example.comiku.data.repository;

import com.example.comiku.data.model.ThematicListCommentData;
import com.example.comiku.data.model.ThematicListData;
import com.example.comiku.data.model.ThematicListVolumeData;
import com.example.comiku.data.model.NotificationType;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ThematicListRepository {
    private static final String COL_LISTAS = "listasTematicas";
    private static final String SUB_TOMOS = "tomosDeLista";
    private static final String SUB_LIKES = "likes";
    private static final String SUB_COMENTARIOS = "comentarios";
    private static final String COL_USUARIO = "usuario";
    private static final String SUB_GUARDADAS = "listasGuardadas";

    private ThematicListRepository() {
    }

    // Crea una nueva lista tematica y devuelve su ID.
    public static Task<String> createThematicList(String uid, String nombre, String descripcion, boolean esGuiaDeLectura) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("UserID", uid);
        datos.put("Nombre", nombre);
        datos.put("Descripcion", descripcion);
        datos.put("CantidadLikes", 0L);
        datos.put("CantidadComentarios", 0L);
        datos.put("FechaCreacion", FieldValue.serverTimestamp());
        datos.put("EsGuiaDeLectura", esGuiaDeLectura);
        datos.put("FotosDePortadas", new ArrayList<>());

        return FirebaseFirestore.getInstance()
                .collection(COL_LISTAS)
                .add(datos)
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw getTaskError(task, "No se pudo crear la lista");
                    }
                    return task.getResult().getId();
                });
    }

    // Actualiza los campos editables de una lista tematica.
    public static Task<Void> updateThematicList(String listId, String nombre, String descripcion, boolean esGuiaDeLectura) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("Nombre", nombre);
        datos.put("Descripcion", descripcion);
        datos.put("EsGuiaDeLectura", esGuiaDeLectura);

        return FirebaseFirestore.getInstance()
                .collection(COL_LISTAS)
                .document(listId)
                .update(datos);
    }

    // Elimina una lista tematica por ID junto con sus subcolecciones.
    public static Task<Void> deleteThematicList(String listId) {
        DocumentReference referenciaLista = FirebaseFirestore.getInstance()
                .collection(COL_LISTAS)
                .document(listId);

        return clearSubcollection(referenciaLista.collection(SUB_TOMOS))
                .continueWithTask(task -> clearSubcollection(referenciaLista.collection(SUB_LIKES)))
                .continueWithTask(task -> referenciaLista.delete());
    }

    // Obtiene una lista tematica por su ID.
    public static Task<ThematicListData> getThematicListById(String listId) {
        return FirebaseFirestore.getInstance()
                .collection(COL_LISTAS)
                .document(listId)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw getTaskError(task, "No se pudo obtener la lista");
                    }
                    DocumentSnapshot doc = task.getResult();
                    if (doc == null || !doc.exists()) {
                        return null;
                    }
                    return buildListData(doc);
                });
    }

    // Obtiene todas las listas tematicas.
    public static Task<List<ThematicListData>> getAllThematicLists() {
        return FirebaseFirestore.getInstance()
                .collection(COL_LISTAS)
                .get()
                .continueWith(task -> buildListCollection(task, "No se pudieron obtener las listas"));
    }

    // Obtiene las listas tematicas creadas por un usuario.
    public static Task<List<ThematicListData>> getUserThematicLists(String uid) {
        return FirebaseFirestore.getInstance()
                .collection(COL_LISTAS)
                .whereEqualTo("UserID", uid)
                .get()
                .continueWith(task -> buildListCollection(task, "No se pudieron obtener las listas del usuario"));
    }

    // Obtiene las listas tematicas guardadas por un usuario.
    public static Task<List<ThematicListData>> getUserSavedThematicLists(String uid) {
        return FirebaseFirestore.getInstance()
                .collection(COL_USUARIO)
                .document(uid)
                .collection(SUB_GUARDADAS)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw getTaskError(task, "No se pudieron obtener las listas guardadas");
                    }
                    QuerySnapshot snapshot = task.getResult();
                    List<String> ids = new ArrayList<>();
                    if (snapshot == null) {
                        return ids;
                    }
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        ids.add(doc.getId());
                    }
                    return ids;
                })
                .continueWithTask(task -> {
                    List<String> ids = task.getResult();
                    if (ids == null || ids.isEmpty()) {
                        return Tasks.forResult(new ArrayList<>());
                    }
                    List<Task<DocumentSnapshot>> tareas = new ArrayList<>();
                    for (String id : ids) {
                        tareas.add(FirebaseFirestore.getInstance()
                                .collection(COL_LISTAS)
                                .document(id)
                                .get());
                    }
                    return Tasks.whenAllSuccess(tareas).continueWith(taskDocumentos -> {
                        List<ThematicListData> listas = new ArrayList<>();
                        if (!taskDocumentos.isSuccessful() || taskDocumentos.getResult() == null) {
                            throw getTaskError(taskDocumentos, "No se pudieron cargar las listas guardadas");
                        }
                        List<?> resultados = taskDocumentos.getResult();
                        for (Object objeto : resultados) {
                            if (objeto instanceof DocumentSnapshot) {
                                ThematicListData lista = buildListData((DocumentSnapshot) objeto);
                                if (lista != null) {
                                    listas.add(lista);
                                }
                            }
                        }
                        return listas;
                    });
                });
    }

    // Agrega un tomo a una lista tematica y devuelve el ID del subdocumento.
    public static Task<String> addVolumeToList(String listId, String comicId, String tomoId, int orden) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("ComicId", comicId);
        datos.put("TomoId", tomoId);
        datos.put("Orden", orden);

        return FirebaseFirestore.getInstance()
                .collection(COL_LISTAS)
                .document(listId)
                .collection(SUB_TOMOS)
                .add(datos)
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw getTaskError(task, "No se pudo agregar el tomo");
                    }
                    return task.getResult().getId();
                });
    }

    // Elimina un tomo de la lista buscando por TomoId.
    public static Task<Void> removeVolumeFromList(String listId, String tomoId) {
        return FirebaseFirestore.getInstance()
                .collection(COL_LISTAS)
                .document(listId)
                .collection(SUB_TOMOS)
                .whereEqualTo("TomoId", tomoId)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw getTaskError(task, "No se pudo quitar el tomo");
                    }
                    QuerySnapshot snapshot = task.getResult();
                    if (snapshot == null || snapshot.isEmpty()) {
                        return Tasks.forResult(null);
                    }
                    List<Task<Void>> borrados = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        borrados.add(doc.getReference().delete());
                    }
                    return Tasks.whenAll(borrados);
                });
    }

    // Borra todos los tomos de una lista para rearmarla.
    public static Task<Void> clearListVolumes(String listId) {
        return clearSubcollection(
                FirebaseFirestore.getInstance()
                        .collection(COL_LISTAS)
                        .document(listId)
                        .collection(SUB_TOMOS)
        );
    }

    // Obtiene todos los tomos de una lista tematica.
    public static Task<List<ThematicListVolumeData>> getListVolumes(String listId) {
        return FirebaseFirestore.getInstance()
                .collection(COL_LISTAS)
                .document(listId)
                .collection(SUB_TOMOS)
                .orderBy("Orden")
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw getTaskError(task, "No se pudieron obtener los tomos de la lista");
                    }
                    List<ThematicListVolumeData> tomos = new ArrayList<>();
                    QuerySnapshot snapshot = task.getResult();
                    if (snapshot == null) {
                        return tomos;
                    }
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Long ordenLong = doc.getLong("Orden");
                        int ordenVal = ordenLong != null ? ordenLong.intValue() : 0;
                        tomos.add(new ThematicListVolumeData(
                                doc.getId(),
                                doc.getString("ComicId"),
                                doc.getString("TomoId"),
                                ordenVal
                        ));
                    }
                    return tomos;
                });
    }

    // Actualiza el array de fotos de portadas de la lista.
    public static Task<Void> updateListPhotos(String listId, List<String> fotos) {
        return FirebaseFirestore.getInstance()
                .collection(COL_LISTAS)
                .document(listId)
                .update("FotosDePortadas", fotos != null ? fotos : new ArrayList<>());
    }

    // Alterna el like del usuario en la lista y devuelve true si quedo likeado.
    public static Task<Boolean> toggleLikeForList(String listId, String uid) {
        DocumentReference refLike = FirebaseFirestore.getInstance()
                .collection(COL_LISTAS)
                .document(listId)
                .collection(SUB_LIKES)
                .document(uid);

        DocumentReference refLista = FirebaseFirestore.getInstance()
                .collection(COL_LISTAS)
                .document(listId);

        return refLike.get().continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw getTaskError(task, "No se pudo revisar el like");
            }
            DocumentSnapshot doc = task.getResult();
            boolean yaLikeado = doc != null && doc.exists();
            if (yaLikeado) {
                return refLike.delete()
                        .continueWithTask(t -> refLista.update("CantidadLikes", FieldValue.increment(-1)))
                        .continueWithTask(t -> refLista.get())
                        .continueWithTask(taskLista -> {
                            if (!taskLista.isSuccessful() || taskLista.getResult() == null || !taskLista.getResult().exists()) {
                                return Tasks.forResult(false);
                            }
                            String uidDuenio = taskLista.getResult().getString("UserID");
                            if (uidDuenio != null && !uidDuenio.equals(uid)) {
                                return NotificationRepository.deleteNotificationsByMetadata(
                                        uidDuenio,
                                        NotificationType.THEMATIC_LIST_LIKE,
                                        "listId",
                                        listId,
                                        uid
                                ).continueWith(taskDeleteNotif -> false);
                            }
                            return Tasks.forResult(false);
                        })
                        .continueWith(t -> false);
            }
            Map<String, Object> datos = new HashMap<>();
            datos.put("UserID", uid);
            datos.put("FechaLike", FieldValue.serverTimestamp());
            return refLike.set(datos)
                    .continueWithTask(t -> refLista.update("CantidadLikes", FieldValue.increment(1)))
                    .continueWithTask(t -> refLista.get())
                    .continueWithTask(taskLista -> {
                        if (!taskLista.isSuccessful() || taskLista.getResult() == null || !taskLista.getResult().exists()) {
                            return Tasks.forResult(true);
                        }
                        String uidDuenio = taskLista.getResult().getString("UserID");
                        if (uidDuenio != null && !uidDuenio.equals(uid)) {
                            Map<String, Object> metadata = new HashMap<>();
                            metadata.put("listId", listId);
                            return NotificationRepository.createNotification(
                                    uidDuenio,
                                    NotificationType.THEMATIC_LIST_LIKE,
                                    uid,
                                    metadata
                            ).continueWith(taskNotificacion -> true);
                        }
                        return Tasks.forResult(true);
                    })
                    .continueWith(t -> true);
        });
    }

    // Verifica si el usuario ya dio like a la lista.
    public static Task<Boolean> getUserLikeStatus(String listId, String uid) {
        return FirebaseFirestore.getInstance()
                .collection(COL_LISTAS)
                .document(listId)
                .collection(SUB_LIKES)
                .document(uid)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw getTaskError(task, "No se pudo verificar el like");
                    }
                    DocumentSnapshot doc = task.getResult();
                    return doc != null && doc.exists();
                });
    }

    // Alterna si el usuario guardo la lista y devuelve true si quedo guardada.
    public static Task<Boolean> toggleSaveListForUser(String listId, String uid) {
        DocumentReference refGuardada = FirebaseFirestore.getInstance()
                .collection(COL_USUARIO)
                .document(uid)
                .collection(SUB_GUARDADAS)
                .document(listId);

        return refGuardada.get().continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw getTaskError(task, "No se pudo revisar la lista guardada");
            }
            DocumentSnapshot doc = task.getResult();
            boolean yaGuardada = doc != null && doc.exists();
            if (yaGuardada) {
                return refGuardada.delete().continueWith(t -> false);
            }
            Map<String, Object> datos = new HashMap<>();
            datos.put("FechaGuardado", FieldValue.serverTimestamp());
            return refGuardada.set(datos).continueWith(t -> true);
        });
    }

    // Verifica si el usuario guardo la lista.
    public static Task<Boolean> getUserSavedListStatus(String listId, String uid) {
        return FirebaseFirestore.getInstance()
                .collection(COL_USUARIO)
                .document(uid)
                .collection(SUB_GUARDADAS)
                .document(listId)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw getTaskError(task, "No se pudo verificar la lista guardada");
                    }
                    DocumentSnapshot doc = task.getResult();
                    return doc != null && doc.exists();
                });
    }

    // Obtiene el nick de un usuario por su UID.
    public static Task<String> getCreatorNick(String uid) {
        return FirebaseFirestore.getInstance()
                .collection(COL_USUARIO)
                .document(uid)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw getTaskError(task, "No se pudo obtener el nick del creador");
                    }
                    DocumentSnapshot doc = task.getResult();
                    if (doc == null || !doc.exists()) {
                        return "Usuario";
                    }
                    String nick = doc.getString("Nick");
                    return nick != null && !nick.isEmpty() ? nick : "Usuario";
                });
    }

    // Convierte una consulta de listas en modelos listos para usar.
    private static List<ThematicListData> buildListCollection(Task<QuerySnapshot> task, String mensajeError) throws Exception {
        if (!task.isSuccessful()) {
            throw getTaskError(task, mensajeError);
        }
        List<ThematicListData> listas = new ArrayList<>();
        QuerySnapshot snapshot = task.getResult();
        if (snapshot == null) {
            return listas;
        }
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            ThematicListData lista = buildListData(doc);
            if (lista != null) {
                listas.add(lista);
            }
        }
        return listas;
    }

    // Borra todos los documentos de una subcoleccion.
    private static Task<Void> clearSubcollection(com.google.firebase.firestore.CollectionReference referencia) {
        return referencia.get().continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw getTaskError(task, "No se pudo limpiar la subcoleccion");
            }
            QuerySnapshot snapshot = task.getResult();
            if (snapshot == null || snapshot.isEmpty()) {
                return Tasks.forResult(null);
            }
            List<Task<Void>> borrados = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                borrados.add(doc.getReference().delete());
            }
            return Tasks.whenAll(borrados);
        });
    }

    // Construye un ThematicListData desde un DocumentSnapshot de Firestore.
    private static ThematicListData buildListData(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return null;
        }

        Long likes = doc.getLong("CantidadLikes");
        Long comentarios = doc.getLong("CantidadComentarios");
        Boolean esGuia = doc.getBoolean("EsGuiaDeLectura");

        Date fechaCreacion = null;
        Object fechaObj = doc.get("FechaCreacion");
        if (fechaObj instanceof Timestamp) {
            fechaCreacion = ((Timestamp) fechaObj).toDate();
        }

        List<String> fotos = new ArrayList<>();
        Object fotosObj = doc.get("FotosDePortadas");
        if (fotosObj instanceof List) {
            for (Object item : (List<?>) fotosObj) {
                if (item != null) {
                    fotos.add(String.valueOf(item));
                }
            }
        }

        return new ThematicListData(
                doc.getId(),
                doc.getString("UserID"),
                doc.getString("Nombre"),
                doc.getString("Descripcion"),
                likes != null ? likes : 0L,
                comentarios != null ? comentarios : 0L,
                fechaCreacion,
                esGuia != null && esGuia,
                fotos
        );
    }

    // Devuelve una excepcion clara para tareas fallidas.
    private static Exception getTaskError(Task<?> tarea, String mensaje) {
        Exception error = tarea.getException();
        return error != null ? error : new Exception(mensaje);
    }

    // Agrega un comentario a una lista tematica y actualiza el contador.
    public static Task<Void> addComment(String listId, String uid, String comentario) {
        DocumentReference refLista = FirebaseFirestore.getInstance()
                .collection(COL_LISTAS)
                .document(listId);

        Map<String, Object> datos = new HashMap<>();
        datos.put("UserID", uid);
        datos.put("Comentario", comentario);
        datos.put("FechaComentario", FieldValue.serverTimestamp());

        return refLista.collection(SUB_COMENTARIOS)
                .add(datos)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw getTaskError(task, "No se pudo agregar el comentario");
                    }
                    return refLista.update("CantidadComentarios", FieldValue.increment(1))
                            .continueWithTask(taskUpdate -> refLista.get())
                            .continueWithTask(taskLista -> {
                                if (!taskLista.isSuccessful() || taskLista.getResult() == null || !taskLista.getResult().exists()) {
                                    return Tasks.forResult(null);
                                }
                                String uidDuenio = taskLista.getResult().getString("UserID");
                                if (uidDuenio != null && !uidDuenio.equals(uid)) {
                                    Map<String, Object> metadata = new HashMap<>();
                                    metadata.put("listId", listId);
                                    return NotificationRepository.createNotification(
                                            uidDuenio,
                                            NotificationType.THEMATIC_LIST_COMMENT,
                                            uid,
                                            metadata
                                    );
                                }
                                return Tasks.forResult(null);
                            });
                });
    }

    // Obtiene los comentarios de una lista tematica.
    public static Task<List<ThematicListCommentData>> getComments(String listId) {
        return FirebaseFirestore.getInstance()
                .collection(COL_LISTAS)
                .document(listId)
                .collection(SUB_COMENTARIOS)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw getTaskError(task, "No se pudieron cargar los comentarios");
                    }
                    List<ThematicListCommentData> comentarios = new ArrayList<>();
                    QuerySnapshot snapshot = task.getResult();
                    if (snapshot == null) return comentarios;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Date fecha = null;
                        Object fechaObj = doc.get("FechaComentario");
                        if (fechaObj instanceof Timestamp) {
                            fecha = ((Timestamp) fechaObj).toDate();
                        }
                        comentarios.add(new ThematicListCommentData(
                                doc.getId(),
                                doc.getString("UserID"),
                                doc.getString("Comentario"),
                                fecha
                        ));
                    }
                    // Ordena por fecha ascendente (mas antiguo primero).
                    comentarios.sort((a, b) -> {
                        long ta = a.fechaComentario != null ? a.fechaComentario.getTime() : 0L;
                        long tb = b.fechaComentario != null ? b.fechaComentario.getTime() : 0L;
                        return Long.compare(ta, tb);
                    });
                    return comentarios;
                });
    }

    // Elimina un comentario solo si pertenece al usuario que lo solicita.
    public static Task<Void> deleteComment(String listId, String commentId, String uid) {
        DocumentReference refComentario = FirebaseFirestore.getInstance()
                .collection(COL_LISTAS)
                .document(listId)
                .collection(SUB_COMENTARIOS)
                .document(commentId);

        DocumentReference refLista = FirebaseFirestore.getInstance()
                .collection(COL_LISTAS)
                .document(listId);

        return refComentario.get().continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw getTaskError(task, "No se pudo verificar el comentario");
            }
            DocumentSnapshot doc = task.getResult();
            if (doc == null || !doc.exists()) {
                throw new Exception("El comentario no existe.");
            }
            String propietario = doc.getString("UserID");
            if (!uid.equals(propietario)) {
                throw new Exception("Solo puedes eliminar tus propios comentarios.");
            }
            return refComentario.delete()
                    .continueWithTask(t -> refLista.update("CantidadComentarios", FieldValue.increment(-1)))
                    .continueWithTask(t -> refLista.get())
                    .continueWithTask(taskLista -> {
                        if (!taskLista.isSuccessful() || taskLista.getResult() == null || !taskLista.getResult().exists()) {
                            return Tasks.forResult(null);
                        }
                        String uidDuenio = taskLista.getResult().getString("UserID");
                        if (uidDuenio != null && !uidDuenio.equals(uid)) {
                            return NotificationRepository.deleteNotificationsByMetadata(
                                    uidDuenio,
                                    NotificationType.THEMATIC_LIST_COMMENT,
                                    "listId",
                                    listId,
                                    uid
                            );
                        }
                        return Tasks.forResult(null);
                    });
        });
    }
}
