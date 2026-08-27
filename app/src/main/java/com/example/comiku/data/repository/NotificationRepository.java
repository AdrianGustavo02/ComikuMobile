package com.example.comiku.data.repository;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.comiku.data.model.AppNotificationData;
import com.example.comiku.data.model.NotificationPageData;
import com.example.comiku.data.model.NotificationType;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NotificationRepository {
    private static final String COL_NOTIFICACIONES = "notificaciones";
    private static final String COL_ACTIVIDADES = "actividades";
    private static final String COL_USUARIO = "usuario";
    private static final long VENTANA_DEDUP_MS = 45_000L;

    private NotificationRepository() {
    }

    // Crea una notificacion solo si el evento es valido y visible para el receptor.
    public static Task<Void> createNotification(
            String receptorId,
            NotificationType tipoNotificacion,
            String actorId,
            Map<String, Object> metadata
    ) {
        if (TextUtils.isEmpty(receptorId) || TextUtils.isEmpty(actorId) || tipoNotificacion == null) {
            return Tasks.forException(new IllegalArgumentException("Datos invalidos para crear notificacion."));
        }
        if (NotificationType.UNKNOWN == tipoNotificacion) {
            return Tasks.forException(new IllegalArgumentException("Tipo de notificacion invalido."));
        }
        if (TextUtils.equals(receptorId, actorId)) {
            return Tasks.forResult(null);
        }

        Map<String, Object> metadataSeguro = metadata != null ? metadata : new HashMap<>();

        return canCreateNotification(receptorId, actorId, tipoNotificacion)
                .continueWithTask(taskPermiso -> {
                    if (!taskPermiso.isSuccessful()) {
                        throw getTaskError(taskPermiso, "No se pudo validar permiso de notificacion.");
                    }
                    if (!Boolean.TRUE.equals(taskPermiso.getResult())) {
                        return Tasks.forResult(null);
                    }

                    return hasRecentDuplicate(receptorId, tipoNotificacion, actorId, metadataSeguro)
                            .continueWithTask(taskDuplicado -> {
                                if (!taskDuplicado.isSuccessful()) {
                                    throw getTaskError(taskDuplicado, "No se pudo validar duplicados.");
                                }
                                if (Boolean.TRUE.equals(taskDuplicado.getResult())) {
                                    return Tasks.forResult(null);
                                }

                                Map<String, Object> datos = new HashMap<>();
                                datos.put("UserID", receptorId);
                                datos.put("type", tipoNotificacion.firestoreValue);
                                datos.put("ActorUserID", actorId);
                                datos.put("metadata", metadataSeguro);
                                datos.put("leido", false);
                                datos.put("fecha", Timestamp.now());

                                return FirebaseFirestore.getInstance()
                                        .collection(COL_NOTIFICACIONES)
                                        .add(datos)
                                        .continueWith(taskCreacion -> {
                                            if (!taskCreacion.isSuccessful()) {
                                                throw getTaskError(taskCreacion, "No se pudo crear la notificacion.");
                                            }
                                            return null;
                                        });
                            });
                });
    }

    // Obtiene una pagina de notificaciones ordenadas por fecha descendente.
    public static Task<NotificationPageData> getNotificationsPage(
            String userId,
            int pageSize,
            @Nullable String cursorId,
            @Nullable Date cursorFecha
    ) {
        if (TextUtils.isEmpty(userId)) {
            return Tasks.forException(new IllegalArgumentException("Usuario invalido."));
        }
        int tamanoPagina = Math.max(1, pageSize);

        Query consulta = FirebaseFirestore.getInstance()
                .collection(COL_NOTIFICACIONES)
                .whereEqualTo("UserID", userId);

        return consulta.get().continueWithTask(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                throw getTaskError(task, "No se pudieron cargar notificaciones.");
            }

            List<AppNotificationData> items = new ArrayList<>();
            for (DocumentSnapshot documento : task.getResult().getDocuments()) {
                if (documento.exists()) {
                    items.add(mapNotificationSnapshot(documento));
                }
            }

            items.sort((izquierda, derecha) -> {
                long fechaIzquierda = izquierda.fechaCreacion != null ? izquierda.fechaCreacion.getTime() : 0L;
                long fechaDerecha = derecha.fechaCreacion != null ? derecha.fechaCreacion.getTime() : 0L;
                int comparacionFecha = Long.compare(fechaDerecha, fechaIzquierda);
                if (comparacionFecha != 0) {
                    return comparacionFecha;
                }
                String idIzquierda = izquierda.id != null ? izquierda.id : "";
                String idDerecha = derecha.id != null ? derecha.id : "";
                return idDerecha.compareTo(idIzquierda);
            });

            int inicio = 0;
            if (!TextUtils.isEmpty(cursorId) && cursorFecha != null) {
                for (int i = 0; i < items.size(); i++) {
                    AppNotificationData item = items.get(i);
                    long fechaItem = item.fechaCreacion != null ? item.fechaCreacion.getTime() : 0L;
                    if (TextUtils.equals(item.id, cursorId) && fechaItem == cursorFecha.getTime()) {
                        inicio = i + 1;
                        break;
                    }
                }
            }

            int fin = Math.min(items.size(), inicio + tamanoPagina + 1);
            List<AppNotificationData> pagina = new ArrayList<>(items.subList(inicio, fin));
            boolean hayMas = pagina.size() > tamanoPagina;
            if (hayMas) {
                pagina = new ArrayList<>(pagina.subList(0, tamanoPagina));
            }

            return hydrateNotificationActors(pagina).continueWithTask(taskHidratacion -> {
                if (!taskHidratacion.isSuccessful() || taskHidratacion.getResult() == null) {
                    throw getTaskError(taskHidratacion, "No se pudieron completar notificaciones.");
                }
                return enrichActivityTypeMetadata(taskHidratacion.getResult())
                        .continueWith(taskEnriquecida -> {
                            if (!taskEnriquecida.isSuccessful() || taskEnriquecida.getResult() == null) {
                                throw getTaskError(taskEnriquecida, "No se pudieron completar tipos de actividad.");
                            }
                            List<AppNotificationData> enriquecidas = taskEnriquecida.getResult();
                            AppNotificationData ultima = enriquecidas.isEmpty() ? null : enriquecidas.get(enriquecidas.size() - 1);
                            return new NotificationPageData(
                                    enriquecidas,
                                    ultima != null ? ultima.id : null,
                                    ultima != null ? ultima.fechaCreacion : null,
                                    hayMas
                            );
                        });
            });
        });
    }

    // Devuelve la cantidad de notificaciones no leidas del usuario.
    public static Task<Integer> getUnreadNotificationsCount(String userId) {
        if (TextUtils.isEmpty(userId)) {
            return Tasks.forResult(0);
        }
        return FirebaseFirestore.getInstance()
                .collection(COL_NOTIFICACIONES)
                .whereEqualTo("UserID", userId)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw getTaskError(task, "No se pudo contar notificaciones no leidas.");
                    }
                    int contador = 0;
                    for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                        Boolean leido = doc.getBoolean("leido");
                        if (leido == null || leido) {
                            continue;
                        }
                        contador++;
                    }
                    return contador;
                });
    }

    // Marca una notificacion puntual como leida.
    public static Task<Void> markNotificationAsRead(String notificationId) {
        if (TextUtils.isEmpty(notificationId)) {
            return Tasks.forException(new IllegalArgumentException("Notificacion invalida."));
        }
        return FirebaseFirestore.getInstance()
                .collection(COL_NOTIFICACIONES)
                .document(notificationId)
                .update("leido", true);
    }

    // Marca todas las notificaciones no leidas del usuario como leidas.
    public static Task<Void> markAllNotificationsAsRead(String userId) {
        if (TextUtils.isEmpty(userId)) {
            return Tasks.forException(new IllegalArgumentException("Usuario invalido."));
        }
        return FirebaseFirestore.getInstance()
                .collection(COL_NOTIFICACIONES)
                .whereEqualTo("UserID", userId)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw getTaskError(task, "No se pudieron cargar notificaciones no leidas.");
                    }
                    QuerySnapshot snapshot = task.getResult();
                    if (snapshot.isEmpty()) {
                        return Tasks.forResult(null);
                    }
                    WriteBatch batch = FirebaseFirestore.getInstance().batch();
                    for (DocumentSnapshot documento : snapshot.getDocuments()) {
                        if (!Boolean.TRUE.equals(documento.getBoolean("leido"))) {
                            batch.update(documento.getReference(), "leido", true);
                        }
                    }
                    return batch.commit();
                });
    }

    // Elimina todas las notificaciones donde el actor coincide con el UID recibido.
    public static Task<Void> deleteNotificationsByActorUid(String userId, String actorUid) {
        if (TextUtils.isEmpty(userId) || TextUtils.isEmpty(actorUid)) {
            return Tasks.forResult(null);
        }
        return FirebaseFirestore.getInstance()
                .collection(COL_NOTIFICACIONES)
                .whereEqualTo("UserID", userId)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw getTaskError(task, "No se pudieron cargar notificaciones por actor.");
                    }
                    WriteBatch batch = FirebaseFirestore.getInstance().batch();
                    int borrados = 0;
                    for (DocumentSnapshot documento : task.getResult().getDocuments()) {
                        if (TextUtils.equals(safeString(documento.getString("ActorUserID")), actorUid)) {
                            batch.delete(documento.getReference());
                            borrados++;
                        }
                    }
                    if (borrados == 0) {
                        return Tasks.forResult(null);
                    }
                    return batch.commit();
                });
    }

    // Elimina la notificacion de solicitud de amistad entre dos usuarios.
    public static Task<Void> deleteFriendRequestNotification(String userId, String actorUid) {
        if (TextUtils.isEmpty(userId) || TextUtils.isEmpty(actorUid)) {
            return Tasks.forResult(null);
        }
        return FirebaseFirestore.getInstance()
                .collection(COL_NOTIFICACIONES)
                .whereEqualTo("UserID", userId)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw getTaskError(task, "No se pudieron cargar notificaciones de solicitud.");
                    }
                    WriteBatch batch = FirebaseFirestore.getInstance().batch();
                    int borrados = 0;
                    for (DocumentSnapshot documento : task.getResult().getDocuments()) {
                        if (NotificationType.FRIEND_REQUEST.firestoreValue.equals(documento.getString("type"))
                                && TextUtils.equals(safeString(documento.getString("ActorUserID")), actorUid)) {
                            batch.delete(documento.getReference());
                            borrados++;
                        }
                    }
                    if (borrados == 0) {
                        return Tasks.forResult(null);
                    }
                    return batch.commit();
                });
    }

    // Marca como leidas las notificaciones de solicitud de amistad de un actor.
    public static Task<Void> markFriendRequestNotificationsAsRead(String userId, String actorUid) {
        if (TextUtils.isEmpty(userId) || TextUtils.isEmpty(actorUid)) {
            return Tasks.forResult(null);
        }
        return FirebaseFirestore.getInstance()
                .collection(COL_NOTIFICACIONES)
                .whereEqualTo("UserID", userId)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw getTaskError(task, "No se pudieron cargar notificaciones de solicitud.");
                    }

                    WriteBatch batch = FirebaseFirestore.getInstance().batch();
                    int actualizadas = 0;
                    for (DocumentSnapshot documento : task.getResult().getDocuments()) {
                        if (NotificationType.FRIEND_REQUEST.firestoreValue.equals(documento.getString("type"))
                                && TextUtils.equals(safeString(documento.getString("ActorUserID")), actorUid)
                                && !Boolean.TRUE.equals(documento.getBoolean("leido"))) {
                            batch.update(documento.getReference(), "leido", true);
                            actualizadas++;
                        }
                    }
                    if (actualizadas == 0) {
                        return Tasks.forResult(null);
                    }
                    return batch.commit();
                });
    }

    // Elimina notificaciones por tipo y metadata especifica.
    public static Task<Void> deleteNotificationsByMetadata(
            String userId,
            NotificationType tipoNotificacion,
            String metadataKey,
            String metadataValue,
            @Nullable String actorUid
    ) {
        if (TextUtils.isEmpty(userId)
                || tipoNotificacion == null
                || TextUtils.isEmpty(metadataKey)
                || TextUtils.isEmpty(metadataValue)) {
            return Tasks.forResult(null);
        }

        return FirebaseFirestore.getInstance()
                .collection(COL_NOTIFICACIONES)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw getTaskError(task, "No se pudieron cargar notificaciones por metadata.");
                    }

                    WriteBatch batch = FirebaseFirestore.getInstance().batch();
                    int borrados = 0;
                    for (DocumentSnapshot documento : task.getResult().getDocuments()) {
                        if (!TextUtils.equals(safeString(documento.getString("UserID")), userId)) {
                            continue;
                        }
                        if (!TextUtils.equals(safeString(documento.getString("type")), tipoNotificacion.firestoreValue)) {
                            continue;
                        }
                        Map<String, Object> metadata = getMap(documento.get("metadata"));
                        String valor = metadata != null ? safeString(metadata.get(metadataKey)) : "";
                        if (!TextUtils.equals(valor, metadataValue)) {
                            continue;
                        }
                        if (!TextUtils.isEmpty(actorUid)
                                && !TextUtils.equals(safeString(documento.getString("ActorUserID")), actorUid)) {
                            continue;
                        }
                        batch.delete(documento.getReference());
                        borrados++;
                    }
                    if (borrados == 0) {
                        return Tasks.forResult(null);
                    }
                    return batch.commit();
                });
    }

    // Verifica permisos y reglas de bloqueo para generar una notificacion.
    private static Task<Boolean> canCreateNotification(String receptorId, String actorId, NotificationType tipo) {
        Task<Boolean> actorBloqueadoPorReceptor = FriendshipRepository.isUserBlockedBy(actorId, receptorId);
        Task<Boolean> receptorBloqueadoPorActor = FriendshipRepository.isUserBlockedBy(receptorId, actorId);

        return Tasks.whenAllSuccess(actorBloqueadoPorReceptor, receptorBloqueadoPorActor)
                .continueWithTask(taskBloqueo -> {
                    if (!taskBloqueo.isSuccessful() || taskBloqueo.getResult() == null || taskBloqueo.getResult().size() < 2) {
                        throw getTaskError(taskBloqueo, "No se pudo validar bloqueos para notificacion.");
                    }
                    boolean bloqueado = Boolean.TRUE.equals(taskBloqueo.getResult().get(0))
                            || Boolean.TRUE.equals(taskBloqueo.getResult().get(1));
                    if (bloqueado) {
                        return Tasks.forResult(false);
                    }
                    if (tipo == NotificationType.FRIEND_REQUEST) {
                        return Tasks.forResult(true);
                    }
                    return FriendshipRepository.areFriends(receptorId, actorId);
                });
    }

    // Evita crear notificaciones repetidas en una ventana corta de tiempo.
    private static Task<Boolean> hasRecentDuplicate(
            String receptorId,
            NotificationType tipoNotificacion,
            String actorId,
            Map<String, Object> metadata
    ) {
        String firmaNueva = buildMetadataSignature(metadata);
        return FirebaseFirestore.getInstance()
                .collection(COL_NOTIFICACIONES)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw getTaskError(task, "No se pudo validar deduplicacion.");
                    }

                    long ahora = System.currentTimeMillis();
                    for (DocumentSnapshot documento : task.getResult().getDocuments()) {
                        if (!TextUtils.equals(safeString(documento.getString("UserID")), receptorId)) {
                            continue;
                        }
                        if (!TextUtils.equals(safeString(documento.getString("type")), tipoNotificacion.firestoreValue)) {
                            continue;
                        }
                        if (!TextUtils.equals(safeString(documento.getString("ActorUserID")), actorId)) {
                            continue;
                        }
                        Date fecha = getDate(documento.get("fecha"));
                        if (fecha == null) {
                            continue;
                        }
                        if (Math.abs(ahora - fecha.getTime()) > VENTANA_DEDUP_MS) {
                            continue;
                        }
                        String firmaExistente = buildMetadataSignature(getMap(documento.get("metadata")));
                        if (TextUtils.equals(firmaNueva, firmaExistente)) {
                            return true;
                        }
                    }
                    return false;
                });
    }

    // Convierte un snapshot en modelo de notificacion.
    private static AppNotificationData mapNotificationSnapshot(DocumentSnapshot documento) {
        return new AppNotificationData(
                documento.getId(),
                safeString(documento.getString("UserID")),
                safeString(documento.getString("ActorUserID")),
                "",
                "",
                NotificationType.fromFirestoreValue(safeString(documento.getString("type"))),
                Boolean.TRUE.equals(documento.getBoolean("leido")),
                getDate(documento.get("fecha")),
                getMap(documento.get("metadata"))
        );
    }

    // Completa nick y foto del actor para mostrar las notificaciones.
    private static Task<List<AppNotificationData>> hydrateNotificationActors(List<AppNotificationData> notificaciones) {
        if (notificaciones == null || notificaciones.isEmpty()) {
            return Tasks.forResult(new ArrayList<>());
        }

        Set<String> uids = new HashSet<>();
        for (AppNotificationData notificacion : notificaciones) {
            if (!TextUtils.isEmpty(notificacion.actorId)) {
                uids.add(notificacion.actorId);
            }
        }

        return loadProfiles(uids).continueWith(taskPerfiles -> {
            if (!taskPerfiles.isSuccessful() || taskPerfiles.getResult() == null) {
                throw getTaskError(taskPerfiles, "No se pudieron completar actores de notificaciones.");
            }
            Map<String, PerfilSimple> perfiles = taskPerfiles.getResult();
            List<AppNotificationData> salida = new ArrayList<>();
            for (AppNotificationData notificacion : notificaciones) {
                PerfilSimple perfil = perfiles.get(notificacion.actorId);
                salida.add(new AppNotificationData(
                        notificacion.id,
                        notificacion.receptorId,
                        notificacion.actorId,
                        perfil != null ? perfil.nick : "",
                        perfil != null ? perfil.fotoDataUrl : "",
                        notificacion.tipoNotificacion,
                        notificacion.leida,
                        notificacion.fechaCreacion,
                        notificacion.metadata
                ));
            }
            return salida;
        });
    }

    // Completa activityType en metadata cuando faltaba en notificaciones antiguas.
    private static Task<List<AppNotificationData>> enrichActivityTypeMetadata(List<AppNotificationData> notificaciones) {
        if (notificaciones == null || notificaciones.isEmpty()) {
            return Tasks.forResult(new ArrayList<>());
        }

        Set<String> activityIds = new HashSet<>();
        for (AppNotificationData notificacion : notificaciones) {
            if (notificacion == null) {
                continue;
            }
            boolean esActividad = notificacion.tipoNotificacion == NotificationType.ACTIVITY_LIKE
                    || notificacion.tipoNotificacion == NotificationType.ACTIVITY_COMMENT;
            if (!esActividad) {
                continue;
            }
            String activityType = safeString(notificacion.metadata.get("activityType"));
            if (!TextUtils.isEmpty(activityType)) {
                continue;
            }
            String activityId = safeString(notificacion.metadata.get("activityId"));
            if (!TextUtils.isEmpty(activityId)) {
                activityIds.add(activityId);
            }
        }

        if (activityIds.isEmpty()) {
            return Tasks.forResult(notificaciones);
        }

        List<String> orden = new ArrayList<>(activityIds);
        List<Task<DocumentSnapshot>> tareas = new ArrayList<>();
        for (String activityId : orden) {
            tareas.add(FirebaseFirestore.getInstance()
                    .collection(COL_ACTIVIDADES)
                    .document(activityId)
                    .get());
        }

        return Tasks.whenAllSuccess(tareas).continueWith(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                throw getTaskError(task, "No se pudieron cargar actividades de notificaciones.");
            }

            Map<String, String> tiposPorActividadId = new HashMap<>();
            List<?> resultados = task.getResult();
            for (int i = 0; i < resultados.size() && i < orden.size(); i++) {
                Object resultado = resultados.get(i);
                if (!(resultado instanceof DocumentSnapshot)) {
                    continue;
                }
                DocumentSnapshot documento = (DocumentSnapshot) resultado;
                if (!documento.exists()) {
                    continue;
                }
                String type = safeString(documento.getString("type"));
                if (!TextUtils.isEmpty(type)) {
                    tiposPorActividadId.put(orden.get(i), type);
                }
            }

            List<AppNotificationData> salida = new ArrayList<>();
            for (AppNotificationData notificacion : notificaciones) {
                if (notificacion == null) {
                    continue;
                }
                boolean esActividad = notificacion.tipoNotificacion == NotificationType.ACTIVITY_LIKE
                        || notificacion.tipoNotificacion == NotificationType.ACTIVITY_COMMENT;
                if (!esActividad) {
                    salida.add(notificacion);
                    continue;
                }

                Map<String, Object> metadata = new HashMap<>(notificacion.metadata != null
                        ? notificacion.metadata
                        : new HashMap<>());
                String activityType = safeString(metadata.get("activityType"));
                if (TextUtils.isEmpty(activityType)) {
                    String activityId = safeString(metadata.get("activityId"));
                    String typeDetectado = tiposPorActividadId.get(activityId);
                    if (!TextUtils.isEmpty(typeDetectado)) {
                        metadata.put("activityType", typeDetectado);
                    }
                }

                salida.add(new AppNotificationData(
                        notificacion.id,
                        notificacion.receptorId,
                        notificacion.actorId,
                        notificacion.actorNick,
                        notificacion.actorFotoPerfilDataUrl,
                        notificacion.tipoNotificacion,
                        notificacion.leida,
                        notificacion.fechaCreacion,
                        metadata
                ));
            }
            return salida;
        });
    }

    // Carga perfiles minimos por UID para hidratar actor de la notificacion.
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
                throw getTaskError(task, "No se pudieron cargar perfiles de notificacion.");
            }
            Map<String, PerfilSimple> perfiles = new HashMap<>();
            List<?> resultados = task.getResult();
            for (int i = 0; i < resultados.size() && i < orden.size(); i++) {
                Object item = resultados.get(i);
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

    // Borra todos los documentos de un snapshot de consulta.
    private static Task<Void> deleteSnapshotDocuments(QuerySnapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return Tasks.forResult(null);
        }
        WriteBatch batch = FirebaseFirestore.getInstance().batch();
        for (DocumentSnapshot documento : snapshot.getDocuments()) {
            batch.delete(documento.getReference());
        }
        return batch.commit();
    }

    // Crea una firma simple de metadata para deduplicar notificaciones.
    private static String buildMetadataSignature(Map<String, Object> metadata) {
        Map<String, Object> meta = metadata != null ? metadata : new HashMap<>();
        String activityId = safeString(meta.get("activityId"));
        String listId = safeString(meta.get("listId"));
        String activityType = safeString(meta.get("activityType"));
        return "activityId=" + activityId + "|listId=" + listId + "|activityType=" + activityType;
    }

    // Convierte un objeto a mapa de forma segura.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Object value) {
        if (!(value instanceof Map)) {
            return new HashMap<>();
        }
        return (Map<String, Object>) value;
    }

    // Convierte un valor en fecha.
    private static Date getDate(Object value) {
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate();
        }
        if (value instanceof Date) {
            return (Date) value;
        }
        return null;
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

    // Convierte un valor a texto seguro.
    private static String safeString(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    // Devuelve un error claro para tareas fallidas.
    private static Exception getTaskError(Task<?> tarea, String mensaje) {
        Exception error = tarea.getException();
        return error != null ? error : new Exception(mensaje);
    }

    // Guarda datos minimos de perfil para hidratar notificaciones.
    private static final class PerfilSimple {
        private final String nick;
        private final String fotoDataUrl;

        private PerfilSimple(String nick, String fotoDataUrl) {
            this.nick = nick != null ? nick : "";
            this.fotoDataUrl = fotoDataUrl != null ? fotoDataUrl : "";
        }
    }
}
