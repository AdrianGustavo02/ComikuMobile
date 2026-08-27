package com.example.comiku.data.repository;

import android.text.TextUtils;

import com.example.comiku.core.validation.InputValidator;
import com.example.comiku.data.model.FriendActivityCommentData;
import com.example.comiku.data.model.FriendActivityCommentPageData;
import com.example.comiku.data.model.FriendActivityData;
import com.example.comiku.data.model.FriendActivityListData;
import com.example.comiku.data.model.FriendActivityPageData;
import com.example.comiku.data.model.FriendActivityType;
import com.example.comiku.data.model.FriendActivityVolumeData;
import com.example.comiku.data.model.NotificationType;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FriendActivityRepository {
    private static final String COL_ACTIVIDADES = "actividades";
    private static final String SUB_LIKES = "likes";
    private static final String SUB_COMENTARIOS = "comentarios";
    private static final String COL_USUARIO = "usuario";
    private static final int MAX_FIRESTORE_IN = 10;
    private static final int MAX_ITEMS_POR_ACTIVIDAD = 20;

    private FriendActivityRepository() {
    }

    // Obtiene una pagina de actividades propias y de amigos
    public static Task<FriendActivityPageData> getActivitiesPage(
            List<String> uidsAmigos,
            int tamanoPagina,
            String uidPropio,
            String cursorId,
            Date cursorFecha
    ) {
        List<String> uidsObjetivo = buildUidsObjetivo(uidsAmigos, uidPropio);
        if (uidsObjetivo.isEmpty()) {
            return Tasks.forResult(new FriendActivityPageData(new ArrayList<>(), null, null, false));
        }

        int tamanoSeguro = Math.max(1, tamanoPagina);
        int tamanoConsulta = Math.max(tamanoSeguro * 3, 30);
        List<List<String>> bloques = splitInChunks(uidsObjetivo, MAX_FIRESTORE_IN);
        List<Task<QuerySnapshot>> tareas = new ArrayList<>();

        for (List<String> bloque : bloques) {
            Query consulta = FirebaseFirestore.getInstance()
                    .collection(COL_ACTIVIDADES)
                    .whereIn("UserID", bloque)
                    .limit(tamanoConsulta);
            tareas.add(consulta.get());
        }

        return Tasks.whenAllSuccess(tareas)
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw getTaskError(task, "No se pudieron cargar las actividades.");
                    }

                    List<FriendActivityData> todas = new ArrayList<>();
                    for (Object item : task.getResult()) {
                        if (!(item instanceof QuerySnapshot)) {
                            continue;
                        }
                        QuerySnapshot snapshot = (QuerySnapshot) item;
                        for (DocumentSnapshot documento : snapshot.getDocuments()) {
                            if (!documento.exists()) {
                                continue;
                            }
                            todas.add(mapActivitySnapshot(documento));
                        }
                    }

                    todas.sort((izq, der) -> {
                        long tiempoIzq = izq.fecha != null ? izq.fecha.getTime() : 0L;
                        long tiempoDer = der.fecha != null ? der.fecha.getTime() : 0L;
                        if (tiempoIzq != tiempoDer) {
                            return Long.compare(tiempoDer, tiempoIzq);
                        }
                        return der.id.compareTo(izq.id);
                    });

                    List<FriendActivityData> filtradas = applyCursorFilter(todas, cursorId, cursorFecha);
                    boolean hayMas = filtradas.size() > tamanoSeguro;
                    List<FriendActivityData> pagina = new ArrayList<>();
                    for (int i = 0; i < Math.min(tamanoSeguro, filtradas.size()); i++) {
                        pagina.add(filtradas.get(i));
                    }

                    return hydrateActivityActors(pagina)
                            .continueWith(taskHidratacion -> {
                                if (!taskHidratacion.isSuccessful() || taskHidratacion.getResult() == null) {
                                    throw getTaskError(taskHidratacion, "No se pudieron completar las actividades.");
                                }
                                List<FriendActivityData> hidratadas = taskHidratacion.getResult();
                                FriendActivityData ultimo = hidratadas.isEmpty()
                                        ? null
                                        : hidratadas.get(hidratadas.size() - 1);
                                return new FriendActivityPageData(
                                        hidratadas,
                                        ultimo != null ? ultimo.id : null,
                                        ultimo != null ? ultimo.fecha : null,
                                        hayMas
                                );
                            });
                });
    }

    // Obtiene una actividad puntual por su ID.
    public static Task<FriendActivityData> getActivityById(String actividadId) {
        if (TextUtils.isEmpty(actividadId)) {
            return Tasks.forResult(null);
        }
        return FirebaseFirestore.getInstance()
                .collection(COL_ACTIVIDADES)
                .document(actividadId)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                        return Tasks.forResult(null);
                    }
                    List<FriendActivityData> base = new ArrayList<>();
                    base.add(mapActivitySnapshot(task.getResult()));
                    return hydrateActivityActors(base).continueWith(taskHidratacion -> {
                        if (!taskHidratacion.isSuccessful() || taskHidratacion.getResult() == null || taskHidratacion.getResult().isEmpty()) {
                            throw getTaskError(taskHidratacion, "No se pudo completar la actividad.");
                        }
                        return taskHidratacion.getResult().get(0);
                    });
                });
    }

    // Alterna el like del usuario sobre una actividad.
    public static Task<Boolean> toggleLikeActivity(String actividadId, String uidUsuario) {
        if (TextUtils.isEmpty(actividadId) || TextUtils.isEmpty(uidUsuario)) {
            return Tasks.forException(new IllegalArgumentException("Actividad y usuario son obligatorios."));
        }

        DocumentReference referenciaActividad = FirebaseFirestore.getInstance()
                .collection(COL_ACTIVIDADES)
                .document(actividadId);
        DocumentReference referenciaLike = referenciaActividad
                .collection(SUB_LIKES)
                .document(uidUsuario);

        return referenciaLike.get()
                .continueWithTask(taskLike -> {
                    if (!taskLike.isSuccessful() || taskLike.getResult() == null) {
                        throw getTaskError(taskLike, "No se pudo leer el estado de like.");
                    }

                    boolean yaExiste = taskLike.getResult().exists();
                    if (yaExiste) {
                        return referenciaLike.delete()
                                .continueWithTask(taskDelete -> {
                                    if (!taskDelete.isSuccessful()) {
                                        throw getTaskError(taskDelete, "No se pudo quitar el like.");
                                    }
                                    return referenciaActividad.update("CantidadLikes", FieldValue.increment(-1))
                                            .continueWithTask(taskUpdate -> {
                                                if (!taskUpdate.isSuccessful()) {
                                                    throw getTaskError(taskUpdate, "No se pudo actualizar los likes.");
                                                }
                                                return referenciaActividad.get().continueWithTask(taskActividad -> {
                                                    if (!taskActividad.isSuccessful() || taskActividad.getResult() == null || !taskActividad.getResult().exists()) {
                                                        return Tasks.forResult(false);
                                                    }
                                                    String uidDuenio = safeString(taskActividad.getResult().getString("UserID"));
                                                    if (TextUtils.isEmpty(uidDuenio) || TextUtils.equals(uidDuenio, uidUsuario)) {
                                                        return Tasks.forResult(false);
                                                    }
                                                    return NotificationRepository.deleteNotificationsByMetadata(
                                                            uidDuenio,
                                                            NotificationType.ACTIVITY_LIKE,
                                                            "activityId",
                                                            actividadId,
                                                            uidUsuario
                                                    ).continueWith(taskDeleteNotif -> false);
                                                });
                                            });
                                });
                    }

                    Map<String, Object> datosLike = new HashMap<>();
                    datosLike.put("UserID", uidUsuario);
                    datosLike.put("fecha", Timestamp.now());
                    return referenciaLike.set(datosLike)
                            .continueWithTask(taskSet -> {
                                if (!taskSet.isSuccessful()) {
                                    throw getTaskError(taskSet, "No se pudo guardar el like.");
                                }
                                return referenciaActividad.update("CantidadLikes", FieldValue.increment(1))
                                        .continueWithTask(taskUpdate -> {
                                            if (!taskUpdate.isSuccessful()) {
                                                throw getTaskError(taskUpdate, "No se pudo actualizar los likes.");
                                            }
                                            return referenciaActividad.get().continueWithTask(taskActividad -> {
                                                if (!taskActividad.isSuccessful() || taskActividad.getResult() == null || !taskActividad.getResult().exists()) {
                                                    return Tasks.forResult(true);
                                                }
                                                DocumentSnapshot actividadDoc = taskActividad.getResult();
                                                String uidDuenio = safeString(actividadDoc.getString("UserID"));
                                                if (TextUtils.isEmpty(uidDuenio) || TextUtils.equals(uidDuenio, uidUsuario)) {
                                                    return Tasks.forResult(true);
                                                }
                                                String tipoActividad = safeString(actividadDoc.getString("type"));
                                                Map<String, Object> metadata = new HashMap<>();
                                                metadata.put("activityId", actividadId);
                                                metadata.put("activityType", tipoActividad);
                                                return NotificationRepository.createNotification(
                                                        uidDuenio,
                                                        NotificationType.ACTIVITY_LIKE,
                                                        uidUsuario,
                                                        metadata
                                                ).continueWith(taskNotif -> true);
                                            });
                                        });
                            });
                });
    }

    // Devuelve si el usuario actual ya dio like a la actividad.
    public static Task<Boolean> getUserLikeStatus(String actividadId, String uidUsuario) {
        if (TextUtils.isEmpty(actividadId) || TextUtils.isEmpty(uidUsuario)) {
            return Tasks.forResult(false);
        }
        return FirebaseFirestore.getInstance()
                .collection(COL_ACTIVIDADES)
                .document(actividadId)
                .collection(SUB_LIKES)
                .document(uidUsuario)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw getTaskError(task, "No se pudo cargar el estado de like.");
                    }
                    DocumentSnapshot documento = task.getResult();
                    return documento != null && documento.exists();
                });
    }

    // Devuelve la cantidad total de likes de una actividad.
    public static Task<Integer> getLikeCount(String actividadId) {
        if (TextUtils.isEmpty(actividadId)) {
            return Tasks.forResult(0);
        }
        return FirebaseFirestore.getInstance()
                .collection(COL_ACTIVIDADES)
                .document(actividadId)
                .collection(SUB_LIKES)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw getTaskError(task, "No se pudo cargar la cantidad de likes.");
                    }
                    return task.getResult().size();
                });
    }

    // Agrega un comentario nuevo a una actividad.
    public static Task<Void> addComment(String actividadId, String uidUsuario, String comentario) {
        String comentarioLimpio = InputValidator.sanitizeForbiddenChars(
                comentario != null ? comentario : ""
        ).trim();
        if (TextUtils.isEmpty(actividadId) || TextUtils.isEmpty(uidUsuario) || TextUtils.isEmpty(comentarioLimpio)) {
            return Tasks.forException(new IllegalArgumentException("Actividad, usuario y comentario son obligatorios."));
        }

        DocumentReference referenciaActividad = FirebaseFirestore.getInstance()
                .collection(COL_ACTIVIDADES)
                .document(actividadId);

        Map<String, Object> datos = new HashMap<>();
        datos.put("UserID", uidUsuario);
        datos.put("texto", comentarioLimpio);
        datos.put("fecha", Timestamp.now());

        return referenciaActividad.collection(SUB_COMENTARIOS)
                .add(datos)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw getTaskError(task, "No se pudo guardar el comentario.");
                    }
                    return referenciaActividad.update("CantidadComentarios", FieldValue.increment(1))
                            .continueWithTask(taskUpdate -> {
                                if (!taskUpdate.isSuccessful()) {
                                    throw getTaskError(taskUpdate, "No se pudo actualizar comentarios.");
                                }
                                return referenciaActividad.get().continueWithTask(taskActividad -> {
                                    if (!taskActividad.isSuccessful() || taskActividad.getResult() == null || !taskActividad.getResult().exists()) {
                                        return Tasks.forResult(null);
                                    }
                                    DocumentSnapshot actividadDoc = taskActividad.getResult();
                                    String uidDuenio = safeString(actividadDoc.getString("UserID"));
                                    if (TextUtils.isEmpty(uidDuenio) || TextUtils.equals(uidDuenio, uidUsuario)) {
                                        return Tasks.forResult(null);
                                    }
                                    String tipoActividad = safeString(actividadDoc.getString("type"));
                                    Map<String, Object> metadata = new HashMap<>();
                                    metadata.put("activityId", actividadId);
                                    metadata.put("activityType", tipoActividad);
                                    return NotificationRepository.createNotification(
                                            uidDuenio,
                                            NotificationType.ACTIVITY_COMMENT,
                                            uidUsuario,
                                            metadata
                                    );
                                });
                            });
                });
    }

    // Elimina un comentario de la actividad si pertenece al usuario actual.
    public static Task<Void> deleteComment(String actividadId, String comentarioId, String uidUsuario) {
        if (TextUtils.isEmpty(actividadId) || TextUtils.isEmpty(comentarioId) || TextUtils.isEmpty(uidUsuario)) {
            return Tasks.forException(new IllegalArgumentException("Actividad, comentario y usuario son obligatorios."));
        }

        DocumentReference referenciaActividad = FirebaseFirestore.getInstance()
                .collection(COL_ACTIVIDADES)
                .document(actividadId);
        DocumentReference referenciaComentario = referenciaActividad
                .collection(SUB_COMENTARIOS)
                .document(comentarioId);

        return referenciaComentario.get()
                .continueWithTask(taskComentario -> {
                    if (!taskComentario.isSuccessful() || taskComentario.getResult() == null) {
                        throw getTaskError(taskComentario, "No se pudo validar el comentario.");
                    }
                    DocumentSnapshot documento = taskComentario.getResult();
                    if (!documento.exists()) {
                        throw new IllegalStateException("No se encontro el comentario.");
                    }
                    String uidAutor = safeString(documento.getString("UserID"));
                    if (!TextUtils.equals(uidAutor, uidUsuario)) {
                        throw new IllegalStateException("Solo puedes eliminar tus propios comentarios.");
                    }
                    return referenciaComentario.delete()
                            .continueWithTask(taskDelete -> {
                                if (!taskDelete.isSuccessful()) {
                                    throw getTaskError(taskDelete, "No se pudo eliminar el comentario.");
                                }
                                return referenciaActividad.update("CantidadComentarios", FieldValue.increment(-1))
                                        .continueWithTask(taskUpdate -> {
                                            if (!taskUpdate.isSuccessful()) {
                                                throw getTaskError(taskUpdate, "No se pudo actualizar comentarios.");
                                            }
                                            return referenciaActividad.get().continueWithTask(taskActividad -> {
                                                if (!taskActividad.isSuccessful() || taskActividad.getResult() == null || !taskActividad.getResult().exists()) {
                                                    return Tasks.forResult(null);
                                                }
                                                String uidDuenio = safeString(taskActividad.getResult().getString("UserID"));
                                                if (TextUtils.isEmpty(uidDuenio) || TextUtils.equals(uidDuenio, uidUsuario)) {
                                                    return Tasks.forResult(null);
                                                }
                                                return NotificationRepository.deleteNotificationsByMetadata(
                                                        uidDuenio,
                                                        NotificationType.ACTIVITY_COMMENT,
                                                        "activityId",
                                                        actividadId,
                                                        uidUsuario
                                                );
                                            });
                                        });
                            });
                });
    }

    // Obtiene comentarios de una actividad con paginacion.
    public static Task<FriendActivityCommentPageData> getCommentsPage(
            String actividadId,
            int tamanoPagina,
            String cursorId,
            Date cursorFecha
    ) {
        if (TextUtils.isEmpty(actividadId)) {
            return Tasks.forException(new IllegalArgumentException("Actividad obligatoria."));
        }

        int tamanoSeguro = Math.max(1, tamanoPagina);
        Query consulta = FirebaseFirestore.getInstance()
                .collection(COL_ACTIVIDADES)
                .document(actividadId)
                .collection(SUB_COMENTARIOS)
                .orderBy("fecha", Query.Direction.DESCENDING)
                .orderBy("__name__", Query.Direction.DESCENDING)
                .limit(tamanoSeguro + 1L);

        if (!TextUtils.isEmpty(cursorId) && cursorFecha != null) {
            consulta = FirebaseFirestore.getInstance()
                    .collection(COL_ACTIVIDADES)
                    .document(actividadId)
                    .collection(SUB_COMENTARIOS)
                    .orderBy("fecha", Query.Direction.DESCENDING)
                    .orderBy("__name__", Query.Direction.DESCENDING)
                    .startAfter(cursorFecha, cursorId)
                    .limit(tamanoSeguro + 1L);
        }

        return consulta.get().continueWithTask(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                throw getTaskError(task, "No se pudieron cargar los comentarios.");
            }

            List<FriendActivityCommentData> comentarios = new ArrayList<>();
            for (DocumentSnapshot documento : task.getResult().getDocuments()) {
                if (documento.exists()) {
                    comentarios.add(mapCommentSnapshot(documento));
                }
            }

            boolean hayMas = comentarios.size() > tamanoSeguro;
            if (hayMas) {
                comentarios = comentarios.subList(0, tamanoSeguro);
            }

            return hydrateCommentAuthors(comentarios).continueWith(taskHidratacion -> {
                if (!taskHidratacion.isSuccessful() || taskHidratacion.getResult() == null) {
                    throw getTaskError(taskHidratacion, "No se pudieron completar los comentarios.");
                }
                List<FriendActivityCommentData> hidratados = taskHidratacion.getResult();
                FriendActivityCommentData ultimo = hidratados.isEmpty()
                        ? null
                        : hidratados.get(hidratados.size() - 1);
                return new FriendActivityCommentPageData(
                        hidratados,
                        ultimo != null ? ultimo.id : null,
                        ultimo != null ? ultimo.fecha : null,
                        hayMas
                );
            });
        });
    }

    // Registra una actividad de tomo agregado y evita duplicados del mismo dia.
    public static Task<Void> appendVolumeActivityForToday(
            String actorUid,
            FriendActivityType tipoActividad,
            String comicId,
            String tomoId,
            String portadaDataUrl
    ) {
        if (TextUtils.isEmpty(actorUid)
                || TextUtils.isEmpty(comicId)
                || TextUtils.isEmpty(tomoId)
                || tipoActividad == null
                || FriendActivityType.UNKNOWN == tipoActividad) {
            return Tasks.forException(new IllegalArgumentException("Datos invalidos para registrar actividad."));
        }

        String dayKey = getDayKeyUtc(new Date());
        return findTodayActivity(actorUid, tipoActividad, dayKey)
                .continueWithTask(taskBusqueda -> {
                    if (!taskBusqueda.isSuccessful()) {
                        throw getTaskError(taskBusqueda, "No se pudo revisar actividades previas.");
                    }
                    DocumentSnapshot existente = taskBusqueda.getResult();
                    if (existente == null || !existente.exists()) {
                        Map<String, Object> payload = new HashMap<>();
                        List<Map<String, Object>> volumenes = new ArrayList<>();
                        volumenes.add(buildVolumePayload(comicId, tomoId, portadaDataUrl));
                        payload.put("count", 1);
                        payload.put("volumes", volumenes);
                        return createNewActivity(actorUid, tipoActividad, dayKey, payload);
                    }

                    Map<String, Object> payloadActual = getMap(existente.get("payload"));
                    List<Map<String, Object>> volumenesActuales = getMapList(payloadActual.get("volumes"));
                    boolean yaIncluido = containsVolume(volumenesActuales, comicId, tomoId);
                    List<Map<String, Object>> siguientes = new ArrayList<>();
                    if (!yaIncluido) {
                        siguientes.add(buildVolumePayload(comicId, tomoId, portadaDataUrl));
                    }
                    siguientes.addAll(volumenesActuales);
                    if (siguientes.size() > MAX_ITEMS_POR_ACTIVIDAD) {
                        siguientes = new ArrayList<>(siguientes.subList(0, MAX_ITEMS_POR_ACTIVIDAD));
                    }

                    int cantidadActual = getInt(payloadActual.get("count"));
                    int siguienteConteo = yaIncluido
                            ? (cantidadActual > 0 ? cantidadActual : volumenesActuales.size())
                            : (cantidadActual > 0 ? cantidadActual : volumenesActuales.size()) + 1;

                    Map<String, Object> payloadNuevo = new HashMap<>();
                    payloadNuevo.put("count", siguienteConteo);
                    payloadNuevo.put("volumes", siguientes);

                    return existente.getReference().update(
                            "payload", payloadNuevo,
                            "fecha", Timestamp.now()
                    );
                });
    }

    // Registra una actividad de lista tematica creada y evita duplicados del mismo dia.
    public static Task<Void> appendThematicListActivityForToday(
            String actorUid,
            String listaId,
            String nombreLista
    ) {
        if (TextUtils.isEmpty(actorUid) || TextUtils.isEmpty(listaId) || TextUtils.isEmpty(nombreLista)) {
            return Tasks.forException(new IllegalArgumentException("Datos invalidos para registrar actividad."));
        }

        String dayKey = getDayKeyUtc(new Date());
        FriendActivityType tipo = FriendActivityType.THEMATIC_LIST_CREATE;
        return findTodayActivity(actorUid, tipo, dayKey)
                .continueWithTask(taskBusqueda -> {
                    if (!taskBusqueda.isSuccessful()) {
                        throw getTaskError(taskBusqueda, "No se pudo revisar actividades previas.");
                    }

                    DocumentSnapshot existente = taskBusqueda.getResult();
                    if (existente == null || !existente.exists()) {
                        Map<String, Object> payload = new HashMap<>();
                        List<Map<String, Object>> listas = new ArrayList<>();
                        listas.add(buildListPayload(listaId, nombreLista));
                        payload.put("count", 1);
                        payload.put("lists", listas);
                        return createNewActivity(actorUid, tipo, dayKey, payload);
                    }

                    Map<String, Object> payloadActual = getMap(existente.get("payload"));
                    List<Map<String, Object>> listasActuales = getMapList(payloadActual.get("lists"));
                    boolean yaIncluida = containsList(listasActuales, listaId);
                    List<Map<String, Object>> siguientes = new ArrayList<>();
                    if (!yaIncluida) {
                        siguientes.add(buildListPayload(listaId, nombreLista));
                    }
                    siguientes.addAll(listasActuales);
                    if (siguientes.size() > MAX_ITEMS_POR_ACTIVIDAD) {
                        siguientes = new ArrayList<>(siguientes.subList(0, MAX_ITEMS_POR_ACTIVIDAD));
                    }

                    int cantidadActual = getInt(payloadActual.get("count"));
                    int siguienteConteo = yaIncluida
                            ? (cantidadActual > 0 ? cantidadActual : listasActuales.size())
                            : (cantidadActual > 0 ? cantidadActual : listasActuales.size()) + 1;

                    Map<String, Object> payloadNuevo = new HashMap<>();
                    payloadNuevo.put("count", siguienteConteo);
                    payloadNuevo.put("lists", siguientes);

                    return existente.getReference().update(
                            "payload", payloadNuevo,
                            "fecha", Timestamp.now()
                    );
                });
    }

    // Busca si hoy ya existe una actividad del mismo tipo para el actor.
    private static Task<DocumentSnapshot> findTodayActivity(
            String actorUid,
            FriendActivityType tipoActividad,
            String dayKey
    ) {
        return FirebaseFirestore.getInstance()
                .collection(COL_ACTIVIDADES)
                .whereEqualTo("UserID", actorUid)
                .whereEqualTo("type", tipoActividad.valorFirestore)
                .whereEqualTo("dayKey", dayKey)
                .limit(1)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw getTaskError(task, "No se pudo buscar actividad del dia.");
                    }
                    QuerySnapshot snapshot = task.getResult();
                    if (snapshot == null || snapshot.isEmpty()) {
                        return null;
                    }
                    return snapshot.getDocuments().get(0);
                });
    }

    // Crea un nuevo documento de actividad en Firestore.
    private static Task<Void> createNewActivity(
            String actorUid,
            FriendActivityType tipoActividad,
            String dayKey,
            Map<String, Object> payload
    ) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("UserID", actorUid);
        datos.put("type", tipoActividad.valorFirestore);
        datos.put("payload", payload);
        datos.put("dayKey", dayKey);
        datos.put("CantidadLikes", 0);
        datos.put("CantidadComentarios", 0);
        datos.put("fecha", Timestamp.now());
        return FirebaseFirestore.getInstance()
                .collection(COL_ACTIVIDADES)
                .add(datos)
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw getTaskError(task, "No se pudo crear la actividad.");
                    }
                    return null;
                });
    }

    // Convierte un documento en modelo de actividad de la app.
    private static FriendActivityData mapActivitySnapshot(DocumentSnapshot documento) {
        String actorUid = safeString(documento.getString("UserID"));
        String actorNick = safeString(documento.getString("actorNick"));
        String actorFoto = safeString(documento.getString("actorFotoPerfil"));
        FriendActivityType tipo = FriendActivityType.fromFirestoreValue(safeString(documento.getString("type")));
        Date fecha = getDate(documento.get("fecha"));
        int likes = getInt(documento.get("CantidadLikes"));
        int comentarios = getInt(documento.get("CantidadComentarios"));

        Map<String, Object> payload = getMap(documento.get("payload"));
        int cantidad = getInt(payload.get("count"));
        List<FriendActivityVolumeData> tomos = parseActivityVolumes(payload.get("volumes"));
        List<FriendActivityListData> listas = parseActivityLists(payload.get("lists"));

        return new FriendActivityData(
                documento.getId(),
                actorUid,
                actorNick,
                actorFoto,
                tipo,
                cantidad,
                tomos,
                listas,
                fecha,
                likes,
                comentarios
        );
    }

    // Convierte un documento en modelo de comentario de actividad.
    private static FriendActivityCommentData mapCommentSnapshot(DocumentSnapshot documento) {
        return new FriendActivityCommentData(
                documento.getId(),
                safeString(documento.getString("UserID")),
                safeString(documento.getString("nick")),
                safeString(documento.getString("fotoPerfil")),
                safeString(documento.getString("texto")),
                getDate(documento.get("fecha"))
        );
    }

    // Completa nick y foto de perfil del actor de cada actividad.
    private static Task<List<FriendActivityData>> hydrateActivityActors(List<FriendActivityData> actividades) {
        if (actividades == null || actividades.isEmpty()) {
            return Tasks.forResult(new ArrayList<>());
        }

        Set<String> uids = new HashSet<>();
        for (FriendActivityData actividad : actividades) {
            if (!TextUtils.isEmpty(actividad.actorUid)) {
                uids.add(actividad.actorUid);
            }
        }

        return loadProfiles(uids).continueWith(taskPerfiles -> {
            if (!taskPerfiles.isSuccessful() || taskPerfiles.getResult() == null) {
                throw getTaskError(taskPerfiles, "No se pudieron cargar perfiles de actividades.");
            }
            Map<String, PerfilSimple> perfiles = taskPerfiles.getResult();
            List<FriendActivityData> salida = new ArrayList<>();
            for (FriendActivityData actividad : actividades) {
                PerfilSimple perfil = perfiles.get(actividad.actorUid);
                String nick = perfil != null && !TextUtils.isEmpty(perfil.nick)
                        ? perfil.nick
                        : actividad.actorNick;
                String foto = perfil != null && !TextUtils.isEmpty(perfil.fotoDataUrl)
                        ? perfil.fotoDataUrl
                        : actividad.actorFotoPerfilDataUrl;
                salida.add(new FriendActivityData(
                        actividad.id,
                        actividad.actorUid,
                        nick,
                        foto,
                        actividad.tipoActividad,
                        actividad.cantidadElementos,
                        actividad.tomos,
                        actividad.listas,
                        actividad.fecha,
                        actividad.cantidadLikes,
                        actividad.cantidadComentarios
                ));
            }
            return salida;
        });
    }

    // Completa nick y foto de perfil del autor de cada comentario.
    private static Task<List<FriendActivityCommentData>> hydrateCommentAuthors(List<FriendActivityCommentData> comentarios) {
        if (comentarios == null || comentarios.isEmpty()) {
            return Tasks.forResult(new ArrayList<>());
        }

        Set<String> uids = new HashSet<>();
        for (FriendActivityCommentData comentario : comentarios) {
            if (!TextUtils.isEmpty(comentario.userId)) {
                uids.add(comentario.userId);
            }
        }

        return loadProfiles(uids).continueWith(taskPerfiles -> {
            if (!taskPerfiles.isSuccessful() || taskPerfiles.getResult() == null) {
                throw getTaskError(taskPerfiles, "No se pudieron cargar perfiles de comentarios.");
            }
            Map<String, PerfilSimple> perfiles = taskPerfiles.getResult();
            List<FriendActivityCommentData> salida = new ArrayList<>();
            for (FriendActivityCommentData comentario : comentarios) {
                PerfilSimple perfil = perfiles.get(comentario.userId);
                String nick = perfil != null && !TextUtils.isEmpty(perfil.nick)
                        ? perfil.nick
                        : comentario.nick;
                String foto = perfil != null && !TextUtils.isEmpty(perfil.fotoDataUrl)
                        ? perfil.fotoDataUrl
                        : comentario.fotoPerfilDataUrl;
                salida.add(new FriendActivityCommentData(
                        comentario.id,
                        comentario.userId,
                        nick,
                        foto,
                        comentario.texto,
                        comentario.fecha
                ));
            }
            return salida;
        });
    }

    // Carga perfiles simples por uid para mostrar nick y foto.
    private static Task<Map<String, PerfilSimple>> loadProfiles(Set<String> uids) {
        if (uids == null || uids.isEmpty()) {
            return Tasks.forResult(new HashMap<>());
        }

        List<Task<DocumentSnapshot>> tareas = new ArrayList<>();
        List<String> orden = new ArrayList<>();
        for (String uid : uids) {
            if (TextUtils.isEmpty(uid)) {
                continue;
            }
            orden.add(uid);
            tareas.add(FirebaseFirestore.getInstance().collection(COL_USUARIO).document(uid).get());
        }

        if (tareas.isEmpty()) {
            return Tasks.forResult(new HashMap<>());
        }

        return Tasks.whenAllSuccess(tareas).continueWith(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                throw getTaskError(task, "No se pudieron leer perfiles.");
            }

            Map<String, PerfilSimple> perfiles = new HashMap<>();
            List<?> documentos = task.getResult();
            for (int i = 0; i < documentos.size() && i < orden.size(); i++) {
                Object item = documentos.get(i);
                if (!(item instanceof DocumentSnapshot)) {
                    continue;
                }
                DocumentSnapshot doc = (DocumentSnapshot) item;
                if (!doc.exists()) {
                    continue;
                }
                perfiles.put(orden.get(i), new PerfilSimple(
                        safeString(doc.getString("Nick")),
                        extractPhotoDataUrl(doc.get("FotoPerfil"))
                ));
            }
            return perfiles;
        });
    }

    // Construye la lista de UIDs que pueden aparecer en la pantalla
    private static List<String> buildUidsObjetivo(List<String> uidsAmigos, String uidPropio) {
        Set<String> unicos = new HashSet<>();
        if (uidsAmigos != null) {
            for (String uid : uidsAmigos) {
                if (!TextUtils.isEmpty(uid)) {
                    unicos.add(uid);
                }
            }
        }
        if (!TextUtils.isEmpty(uidPropio)) {
            unicos.add(uidPropio);
        }
        List<String> salida = new ArrayList<>(unicos);
        Collections.sort(salida);
        return salida;
    }

    // Aplica el cursor local para mantener orden estable entre paginas.
    private static List<FriendActivityData> applyCursorFilter(List<FriendActivityData> actividades, String cursorId, Date cursorFecha) {
        if (TextUtils.isEmpty(cursorId) || cursorFecha == null || actividades == null || actividades.isEmpty()) {
            return actividades != null ? actividades : new ArrayList<>();
        }

        List<FriendActivityData> filtradas = new ArrayList<>();
        long tiempoCursor = cursorFecha.getTime();
        for (FriendActivityData actividad : actividades) {
            long tiempoActividad = actividad.fecha != null ? actividad.fecha.getTime() : 0L;
            if (tiempoActividad < tiempoCursor) {
                filtradas.add(actividad);
                continue;
            }
            if (tiempoActividad == tiempoCursor && actividad.id.compareTo(cursorId) < 0) {
                filtradas.add(actividad);
            }
        }
        return filtradas;
    }

    // Crea bloques de tamano fijo para consultas whereIn.
    private static List<List<String>> splitInChunks(List<String> valores, int tamanoBloque) {
        List<List<String>> bloques = new ArrayList<>();
        if (valores == null || valores.isEmpty()) {
            return bloques;
        }
        int tamano = Math.max(1, tamanoBloque);
        for (int i = 0; i < valores.size(); i += tamano) {
            int hasta = Math.min(i + tamano, valores.size());
            bloques.add(new ArrayList<>(valores.subList(i, hasta)));
        }
        return bloques;
    }

    // Arma la carga minima para una actividad de tomo.
    private static Map<String, Object> buildVolumePayload(String comicId, String tomoId, String portadaDataUrl) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("comicId", comicId);
        datos.put("volumeId", tomoId);
        Map<String, Object> portada = new HashMap<>();
        portada.put("dataUrl", portadaDataUrl != null ? portadaDataUrl : "");
        datos.put("portada", portada);
        return datos;
    }

    // Arma la carga minima para una actividad de lista tematica.
    private static Map<String, Object> buildListPayload(String listaId, String nombreLista) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("id", listaId);
        datos.put("name", nombreLista);
        return datos;
    }

    // Verifica si una lista de payload ya contiene un tomo especifico.
    private static boolean containsVolume(List<Map<String, Object>> volumenes, String comicId, String tomoId) {
        if (volumenes == null || volumenes.isEmpty()) {
            return false;
        }
        for (Map<String, Object> volumen : volumenes) {
            if (TextUtils.equals(safeString(volumen.get("comicId")), comicId)
                    && TextUtils.equals(safeString(volumen.get("volumeId")), tomoId)) {
                return true;
            }
        }
        return false;
    }

    // Verifica si una lista de payload ya contiene una lista tematica.
    private static boolean containsList(List<Map<String, Object>> listas, String listaId) {
        if (listas == null || listas.isEmpty()) {
            return false;
        }
        for (Map<String, Object> lista : listas) {
            if (TextUtils.equals(safeString(lista.get("id")), listaId)) {
                return true;
            }
        }
        return false;
    }

    // Parsea el arreglo de tomos dentro del payload de actividad.
    private static List<FriendActivityVolumeData> parseActivityVolumes(Object valor) {
        List<FriendActivityVolumeData> salida = new ArrayList<>();
        List<Map<String, Object>> items = getMapList(valor);
        for (Map<String, Object> item : items) {
            Map<String, Object> portada = getMap(item.get("portada"));
            salida.add(new FriendActivityVolumeData(
                    safeString(item.get("comicId")),
                    safeString(item.get("volumeId")),
                    safeString(portada.get("dataUrl"))
            ));
        }
        return salida;
    }

    // Parsea el arreglo de listas dentro del payload de actividad.
    private static List<FriendActivityListData> parseActivityLists(Object valor) {
        List<FriendActivityListData> salida = new ArrayList<>();
        List<Map<String, Object>> items = getMapList(valor);
        for (Map<String, Object> item : items) {
            salida.add(new FriendActivityListData(
                    safeString(item.get("id")),
                    safeString(item.get("name"))
            ));
        }
        return salida;
    }

    // Convierte un objeto en lista de mapas de forma segura.
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> getMapList(Object valor) {
        if (!(valor instanceof List)) {
            return new ArrayList<>();
        }
        List<?> lista = (List<?>) valor;
        List<Map<String, Object>> salida = new ArrayList<>();
        for (Object item : lista) {
            if (item instanceof Map) {
                salida.add((Map<String, Object>) item);
            }
        }
        return salida;
    }

    // Convierte un objeto en mapa de forma segura.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Object valor) {
        if (!(valor instanceof Map)) {
            return new HashMap<>();
        }
        return (Map<String, Object>) valor;
    }

    // Convierte un valor en fecha.
    private static Date getDate(Object valor) {
        if (valor instanceof Timestamp) {
            return ((Timestamp) valor).toDate();
        }
        if (valor instanceof Date) {
            return (Date) valor;
        }
        return null;
    }

    // Convierte un valor en entero con fallback a cero.
    private static int getInt(Object valor) {
        if (valor instanceof Number) {
            return ((Number) valor).intValue();
        }
        return 0;
    }

    // Extrae la foto de perfil en formato dataUrl.
    private static String extractPhotoDataUrl(Object fotoPerfil) {
        if (fotoPerfil instanceof String) {
            return String.valueOf(fotoPerfil);
        }
        if (!(fotoPerfil instanceof Map)) {
            return "";
        }
        Map<?, ?> mapa = (Map<?, ?>) fotoPerfil;
        Object dataUrl = mapa.get("dataUrl");
        return dataUrl != null ? String.valueOf(dataUrl) : "";
    }

    // Crea una clave de dia en UTC para agrupar actividades.
    private static String getDayKeyUtc(Date fecha) {
        Calendar calendario = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        calendario.setTime(fecha != null ? fecha : new Date());
        int anio = calendario.get(Calendar.YEAR);
        int mes = calendario.get(Calendar.MONTH) + 1;
        int dia = calendario.get(Calendar.DAY_OF_MONTH);
        return String.format(Locale.US, "%04d-%02d-%02d", anio, mes, dia);
    }

    // Convierte un valor en texto seguro.
    private static String safeString(Object valor) {
        return valor != null ? String.valueOf(valor) : "";
    }

    // Devuelve una excepcion clara cuando una tarea falla.
    private static Exception getTaskError(Task<?> tarea, String mensaje) {
        Exception error = tarea.getException();
        return error != null ? error : new Exception(mensaje);
    }

    // Estructura simple para cachear datos minimos de perfil.
    private static final class PerfilSimple {
        private final String nick;
        private final String fotoDataUrl;

        private PerfilSimple(String nick, String fotoDataUrl) {
            this.nick = nick != null ? nick : "";
            this.fotoDataUrl = fotoDataUrl != null ? fotoDataUrl : "";
        }
    }
}
