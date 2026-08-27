package com.example.comiku.data.repository;

import android.text.TextUtils;
import android.util.Log;

import com.example.comiku.data.model.BlockedUserData;
import com.example.comiku.data.model.FriendRequestData;
import com.example.comiku.data.model.NotificationType;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FriendshipRepository {
    private static final String TAG = "FriendshipRepository";
    private static final String COLECCION_USUARIO = "usuario";
    private static final String SUBCOLECCION_AMIGOS = "Amigos";
    private static final String SUBCOLECCION_SOLICITUDES = "SolicitudesAmistad";
    private static final String SUBCOLECCION_BLOQUEADOS = "UsuariosBloqueados";
    private static final String SUBCOLECCION_LISTAS_GUARDADAS = "listasGuardadas";
    private static final String COLECCION_LISTAS_TEMATICAS = "listasTematicas";

    private FriendshipRepository() {
    }

    // Envia una solicitud de amistad al usuario destino.
    public static Task<Void> sendFriendRequest(String uidOrigen, String uidDestino) {
        if (TextUtils.isEmpty(uidOrigen) || TextUtils.isEmpty(uidDestino)) {
            return Tasks.forException(new IllegalArgumentException("UID invalido."));
        }
        if (uidOrigen.equals(uidDestino)) {
            return Tasks.forException(new IllegalArgumentException("No puedes enviarte solicitud a ti mismo."));
        }

        return isUserBlockedBy(uidOrigen, uidDestino)
                .continueWithTask(taskBloqueo -> {
                    if (!taskBloqueo.isSuccessful()) {
                        throw getTaskError(taskBloqueo, "No se pudo validar el bloqueo.");
                    }
                    Boolean bloqueado = taskBloqueo.getResult();
                    if (Boolean.TRUE.equals(bloqueado)) {
                        return Tasks.forException(new IllegalStateException("No puedes enviar solicitud a este usuario."));
                    }
                    return FirebaseFirestore.getInstance()
                            .collection(COLECCION_USUARIO)
                            .document(uidDestino)
                            .collection(SUBCOLECCION_SOLICITUDES)
                            .document(uidOrigen)
                            .get();
                })
                .continueWithTask(taskSolicitud -> {
                    if (!taskSolicitud.isSuccessful()) {
                        throw getTaskError(taskSolicitud, "No se pudo revisar la solicitud.");
                    }
                    DocumentSnapshot documentoSolicitud = taskSolicitud.getResult();
                    if (documentoSolicitud != null && documentoSolicitud.exists()) {
                        return Tasks.forException(new IllegalStateException("Ya existe una solicitud pendiente."));
                    }
                    return FirebaseFirestore.getInstance()
                            .collection(COLECCION_USUARIO)
                            .document(uidOrigen)
                            .get();
                })
                .continueWithTask(taskPerfilOrigen -> {
                    if (!taskPerfilOrigen.isSuccessful() || taskPerfilOrigen.getResult() == null) {
                        throw getTaskError(taskPerfilOrigen, "No se pudo cargar tu perfil.");
                    }
                    DocumentSnapshot documentoPerfil = taskPerfilOrigen.getResult();
                    if (!documentoPerfil.exists()) {
                        return Tasks.forException(new IllegalStateException("No se encontro el perfil del remitente."));
                    }

                    Map<String, Object> datosSolicitud = new HashMap<>();
                    datosSolicitud.put("UserID", uidOrigen);
                    datosSolicitud.put("Nick", safeString(documentoPerfil.getString("Nick")));
                    datosSolicitud.put("FotoPerfil", extractPhotoDataUrl(documentoPerfil.get("FotoPerfil")));
                    datosSolicitud.put("fechaSolicitud", Timestamp.now());

                    return FirebaseFirestore.getInstance()
                            .collection(COLECCION_USUARIO)
                            .document(uidDestino)
                            .collection(SUBCOLECCION_SOLICITUDES)
                            .document(uidOrigen)
                            .set(datosSolicitud)
                            .continueWithTask(taskSet -> {
                                if (!taskSet.isSuccessful()) {
                                    throw getTaskError(taskSet, "No se pudo crear la solicitud.");
                                }
                                return NotificationRepository.createNotification(
                                        uidDestino,
                                        NotificationType.FRIEND_REQUEST,
                                        uidOrigen,
                                        new HashMap<>()
                                );
                            });
                });
    }

    // Cancela una solicitud enviada por el usuario origen.
    public static Task<Void> cancelFriendRequest(String uidOrigen, String uidDestino) {
        if (TextUtils.isEmpty(uidOrigen) || TextUtils.isEmpty(uidDestino)) {
            return Tasks.forException(new IllegalArgumentException("UID invalido."));
        }

        return FirebaseFirestore.getInstance()
                .collection(COLECCION_USUARIO)
                .document(uidDestino)
                .collection(SUBCOLECCION_SOLICITUDES)
                .document(uidOrigen)
                .delete()
                .continueWithTask(taskDelete -> {
                    if (!taskDelete.isSuccessful()) {
                        throw getTaskError(taskDelete, "No se pudo cancelar la solicitud.");
                    }
                    return NotificationRepository.deleteFriendRequestNotification(uidDestino, uidOrigen);
                });
    }

    // Devuelve true si hay una solicitud enviada al usuario destino.
    public static Task<Boolean> hasSentRequest(String uidOrigen, String uidDestino) {
        if (TextUtils.isEmpty(uidOrigen) || TextUtils.isEmpty(uidDestino)) {
            return Tasks.forResult(false);
        }

        return FirebaseFirestore.getInstance()
                .collection(COLECCION_USUARIO)
                .document(uidDestino)
                .collection(SUBCOLECCION_SOLICITUDES)
                .document(uidOrigen)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw getTaskError(task, "No se pudo revisar la solicitud enviada.");
                    }
                    DocumentSnapshot documento = task.getResult();
                    return documento != null && documento.exists();
                });
    }

    // Obtiene solicitudes pendientes que recibio el usuario actual.
    public static Task<List<FriendRequestData>> getFriendRequests(String uidReceptor) {
        if (TextUtils.isEmpty(uidReceptor)) {
            return Tasks.forResult(new ArrayList<>());
        }

        return FirebaseFirestore.getInstance()
                .collection(COLECCION_USUARIO)
                .document(uidReceptor)
                .collection(SUBCOLECCION_SOLICITUDES)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw getTaskError(task, "No se pudieron cargar las solicitudes.");
                    }

                    List<FriendRequestData> solicitudes = new ArrayList<>();
                    QuerySnapshot snapshot = task.getResult();
                    for (DocumentSnapshot documento : snapshot.getDocuments()) {
                        String senderUid = safeString((String) documento.get("UserID"));
                        if (TextUtils.isEmpty(senderUid)) {
                            senderUid = documento.getId();
                        }

                        Date fechaSolicitud = null;
                        Object fechaObj = documento.get("fechaSolicitud");
                        if (fechaObj instanceof Timestamp) {
                            fechaSolicitud = ((Timestamp) fechaObj).toDate();
                        }

                        solicitudes.add(new FriendRequestData(
                                senderUid,
                                safeString(documento.getString("Nick")),
                                extractPhotoDataUrl(documento.get("FotoPerfil")),
                                fechaSolicitud
                        ));
                    }

                    solicitudes.sort((solicitudA, solicitudB) -> {
                        long tiempoA = solicitudA.fechaSolicitud != null ? solicitudA.fechaSolicitud.getTime() : 0L;
                        long tiempoB = solicitudB.fechaSolicitud != null ? solicitudB.fechaSolicitud.getTime() : 0L;
                        return Long.compare(tiempoB, tiempoA);
                    });

                    return solicitudes;
                });
    }

    // Acepta una solicitud y crea amistad en ambos perfiles.
    public static Task<Void> acceptFriendRequest(String uidReceptor, String uidEmisor) {
        if (TextUtils.isEmpty(uidReceptor) || TextUtils.isEmpty(uidEmisor)) {
            return Tasks.forException(new IllegalArgumentException("UID invalido."));
        }

        return isUserBlockedBy(uidReceptor, uidEmisor)
                .continueWithTask(taskBloqueoReceptor -> {
                    if (!taskBloqueoReceptor.isSuccessful()) {
                        throw getTaskError(taskBloqueoReceptor, "No se pudo validar bloqueo.");
                    }
                    if (Boolean.TRUE.equals(taskBloqueoReceptor.getResult())) {
                        return Tasks.forException(new IllegalStateException("No puedes aceptar esta solicitud."));
                    }
                    return isUserBlockedBy(uidEmisor, uidReceptor);
                })
                .continueWithTask(taskBloqueoEmisor -> {
                    if (!taskBloqueoEmisor.isSuccessful()) {
                        throw getTaskError(taskBloqueoEmisor, "No se pudo validar bloqueo.");
                    }
                    if (Boolean.TRUE.equals(taskBloqueoEmisor.getResult())) {
                        return Tasks.forException(new IllegalStateException("No puedes aceptar esta solicitud."));
                    }
                    return FirebaseFirestore.getInstance()
                            .collection(COLECCION_USUARIO)
                            .document(uidReceptor)
                            .collection(SUBCOLECCION_SOLICITUDES)
                            .document(uidEmisor)
                            .get();
                })
                .continueWithTask(taskSolicitud -> {
                    if (!taskSolicitud.isSuccessful() || taskSolicitud.getResult() == null) {
                        throw getTaskError(taskSolicitud, "No se pudo revisar la solicitud.");
                    }
                    if (!taskSolicitud.getResult().exists()) {
                        return Tasks.forException(new IllegalStateException("La solicitud ya no existe."));
                    }

                    FirebaseFirestore firestore = FirebaseFirestore.getInstance();
                    WriteBatch batch = firestore.batch();
                    Timestamp ahora = Timestamp.now();

                    DocumentReference refAmigoReceptor = firestore.collection(COLECCION_USUARIO)
                            .document(uidReceptor)
                            .collection(SUBCOLECCION_AMIGOS)
                            .document(uidEmisor);
                    Map<String, Object> datosAmigoReceptor = new HashMap<>();
                    datosAmigoReceptor.put("UserID", uidEmisor);
                    datosAmigoReceptor.put("fechaAmistad", ahora);

                    DocumentReference refAmigoEmisor = firestore.collection(COLECCION_USUARIO)
                            .document(uidEmisor)
                            .collection(SUBCOLECCION_AMIGOS)
                            .document(uidReceptor);
                    Map<String, Object> datosAmigoEmisor = new HashMap<>();
                    datosAmigoEmisor.put("UserID", uidReceptor);
                    datosAmigoEmisor.put("fechaAmistad", ahora);

                    DocumentReference refSolicitud = firestore.collection(COLECCION_USUARIO)
                            .document(uidReceptor)
                            .collection(SUBCOLECCION_SOLICITUDES)
                            .document(uidEmisor);

                    batch.set(refAmigoReceptor, datosAmigoReceptor);
                    batch.set(refAmigoEmisor, datosAmigoEmisor);
                    batch.delete(refSolicitud);
                    batch.update(firestore.collection(COLECCION_USUARIO).document(uidReceptor),
                            "cantidadAmigos", FieldValue.increment(1));
                    batch.update(firestore.collection(COLECCION_USUARIO).document(uidEmisor),
                            "cantidadAmigos", FieldValue.increment(1));

                    return batch.commit().continueWithTask(taskCommit -> {
                        if (!taskCommit.isSuccessful()) {
                            throw getTaskError(taskCommit, "No se pudo aceptar la solicitud.");
                        }
                        return NotificationRepository.markFriendRequestNotificationsAsRead(uidReceptor, uidEmisor);
                    });
                });
    }

    // Rechaza una solicitud pendiente recibida.
    public static Task<Void> declineFriendRequest(String uidReceptor, String uidEmisor) {
        if (TextUtils.isEmpty(uidReceptor) || TextUtils.isEmpty(uidEmisor)) {
            return Tasks.forException(new IllegalArgumentException("UID invalido."));
        }

        return FirebaseFirestore.getInstance()
                .collection(COLECCION_USUARIO)
                .document(uidReceptor)
                .collection(SUBCOLECCION_SOLICITUDES)
                .document(uidEmisor)
                .delete()
                .continueWithTask(taskDelete -> {
                    if (!taskDelete.isSuccessful()) {
                        throw getTaskError(taskDelete, "No se pudo rechazar la solicitud.");
                    }
                    return NotificationRepository.deleteFriendRequestNotification(uidReceptor, uidEmisor);
                });
    }

    // Devuelve true cuando ambos usuarios ya son amigos.
    public static Task<Boolean> areFriends(String uidA, String uidB) {
        if (TextUtils.isEmpty(uidA) || TextUtils.isEmpty(uidB)) {
            return Tasks.forResult(false);
        }

        return FirebaseFirestore.getInstance()
                .collection(COLECCION_USUARIO)
                .document(uidA)
                .collection(SUBCOLECCION_AMIGOS)
                .document(uidB)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw getTaskError(task, "No se pudo validar amistad.");
                    }
                    DocumentSnapshot documento = task.getResult();
                    return documento != null && documento.exists();
                });
    }

    // Elimina amistad en ambos usuarios y ajusta contador.
    public static Task<Void> removeFriend(String uidA, String uidB) {
        if (TextUtils.isEmpty(uidA) || TextUtils.isEmpty(uidB)) {
            return Tasks.forException(new IllegalArgumentException("UID invalido."));
        }

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        WriteBatch batch = firestore.batch();

        batch.delete(firestore.collection(COLECCION_USUARIO)
                .document(uidA)
                .collection(SUBCOLECCION_AMIGOS)
                .document(uidB));
        batch.delete(firestore.collection(COLECCION_USUARIO)
                .document(uidB)
                .collection(SUBCOLECCION_AMIGOS)
                .document(uidA));
        batch.update(firestore.collection(COLECCION_USUARIO).document(uidA),
                "cantidadAmigos", FieldValue.increment(-1));
        batch.update(firestore.collection(COLECCION_USUARIO).document(uidB),
                "cantidadAmigos", FieldValue.increment(-1));

        return batch.commit().continueWithTask(taskCommit -> {
            if (!taskCommit.isSuccessful()) {
                throw getTaskError(taskCommit, "No se pudo eliminar la amistad.");
            }
            return NotificationRepository.deleteNotificationsByActorUid(uidA, uidB)
                    .continueWithTask(tareaA -> NotificationRepository.deleteNotificationsByActorUid(uidB, uidA));
        });
    }

    // Bloquea a un usuario y limpia la relacion previa sin revertir el bloqueo.
    public static Task<Void> blockUser(String uidBloqueador, String uidBloqueado) {
        if (TextUtils.isEmpty(uidBloqueador) || TextUtils.isEmpty(uidBloqueado)) {
            return Tasks.forException(new IllegalArgumentException("UID invalido."));
        }
        if (TextUtils.equals(uidBloqueador, uidBloqueado)) {
            return Tasks.forException(new IllegalArgumentException("No puedes bloquearte a ti mismo."));
        }

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        DocumentReference refBloqueado = firestore.collection(COLECCION_USUARIO)
                .document(uidBloqueado);

        return refBloqueado.get()
                .continueWithTask(taskUsuario -> {
                    if (!taskUsuario.isSuccessful() || taskUsuario.getResult() == null) {
                        throw getTaskError(taskUsuario, "No se pudo validar el usuario a bloquear.");
                    }
                    if (!taskUsuario.getResult().exists()) {
                        return Tasks.forException(new IllegalStateException("No se encontro el usuario a bloquear."));
                    }

                    WriteBatch batch = firestore.batch();
                    DocumentReference refBloqueo = firestore.collection(COLECCION_USUARIO)
                            .document(uidBloqueador)
                            .collection(SUBCOLECCION_BLOQUEADOS)
                            .document(uidBloqueado);

                    batch.set(refBloqueo, createBlockedUserData(uidBloqueado));

                    return areFriends(uidBloqueador, uidBloqueado)
                            .continueWithTask(taskAmistad -> {
                                if (!taskAmistad.isSuccessful()) {
                                    throw getTaskError(taskAmistad, "No se pudo validar la amistad.");
                                }

                                if (Boolean.TRUE.equals(taskAmistad.getResult())) {
                                    batch.delete(firestore.collection(COLECCION_USUARIO)
                                            .document(uidBloqueador)
                                            .collection(SUBCOLECCION_AMIGOS)
                                            .document(uidBloqueado));
                                    batch.delete(firestore.collection(COLECCION_USUARIO)
                                            .document(uidBloqueado)
                                            .collection(SUBCOLECCION_AMIGOS)
                                            .document(uidBloqueador));
                                    batch.update(firestore.collection(COLECCION_USUARIO).document(uidBloqueador),
                                            "cantidadAmigos", FieldValue.increment(-1));
                                    batch.update(firestore.collection(COLECCION_USUARIO).document(uidBloqueado),
                                            "cantidadAmigos", FieldValue.increment(-1));
                                }

                                return batch.commit().continueWithTask(taskCommit -> {
                                    if (!taskCommit.isSuccessful()) {
                                        throw getTaskError(taskCommit, "No se pudo guardar el bloqueo.");
                                    }
                                    return runBlockCleanupTasks(uidBloqueador, uidBloqueado);
                                });
                            });
                });
    }

    // Desbloquea a un usuario sin restaurar relaciones previas.
    public static Task<Void> unblockUser(String uidBloqueador, String uidBloqueado) {
        if (TextUtils.isEmpty(uidBloqueador) || TextUtils.isEmpty(uidBloqueado)) {
            return Tasks.forException(new IllegalArgumentException("UID invalido."));
        }

        return FirebaseFirestore.getInstance()
                .collection(COLECCION_USUARIO)
                .document(uidBloqueador)
                .collection(SUBCOLECCION_BLOQUEADOS)
                .document(uidBloqueado)
                .delete()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw getTaskError(task, "No se pudo desbloquear el usuario.");
                    }
                    return null;
                });
    }

    // Obtiene la lista de usuarios bloqueados con datos para la pantalla.
    public static Task<List<BlockedUserData>> getBlockedUsers(String uidBloqueador) {
        if (TextUtils.isEmpty(uidBloqueador)) {
            return Tasks.forResult(new ArrayList<>());
        }

        return FirebaseFirestore.getInstance()
                .collection(COLECCION_USUARIO)
                .document(uidBloqueador)
                .collection(SUBCOLECCION_BLOQUEADOS)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw getTaskError(task, "No se pudo cargar usuarios bloqueados.");
                    }

                    List<Task<DocumentSnapshot>> tareasPerfiles = new ArrayList<>();
                    List<Date> fechasBloqueo = new ArrayList<>();

                    for (DocumentSnapshot documento : task.getResult().getDocuments()) {
                        String uidBloqueado = safeString((String) documento.get("UserID"));
                        if (TextUtils.isEmpty(uidBloqueado)) {
                            uidBloqueado = documento.getId();
                        }
                        if (TextUtils.isEmpty(uidBloqueado)) {
                            continue;
                        }
                        tareasPerfiles.add(FirebaseFirestore.getInstance()
                                .collection(COLECCION_USUARIO)
                                .document(uidBloqueado)
                                .get());
                        Object fechaObj = documento.get("fechaBloqueo");
                        fechasBloqueo.add(fechaObj instanceof Timestamp ? ((Timestamp) fechaObj).toDate() : null);
                    }

                    if (tareasPerfiles.isEmpty()) {
                        return Tasks.forResult(new ArrayList<>());
                    }

                    return Tasks.whenAllSuccess(tareasPerfiles).continueWith(taskPerfiles -> {
                        if (!taskPerfiles.isSuccessful() || taskPerfiles.getResult() == null) {
                            throw getTaskError(taskPerfiles, "No se pudieron cargar los perfiles bloqueados.");
                        }

                        List<BlockedUserData> bloqueados = new ArrayList<>();
                        List<?> resultados = taskPerfiles.getResult();
                        for (int i = 0; i < resultados.size(); i++) {
                            Object item = resultados.get(i);
                            if (!(item instanceof DocumentSnapshot)) {
                                continue;
                            }

                            DocumentSnapshot perfil = (DocumentSnapshot) item;
                            if (!perfil.exists()) {
                                continue;
                            }

                            bloqueados.add(new BlockedUserData(
                                    perfil.getId(),
                                    safeString(perfil.getString("Nick")),
                                    extractPhotoDataUrl(perfil.get("FotoPerfil")),
                                    i < fechasBloqueo.size() ? fechasBloqueo.get(i) : null
                            ));
                        }
                        return bloqueados;
                    });
                });
    }

    // Devuelve true si el perfil se puede abrir sin conflicto de bloqueo.
    public static Task<Boolean> canOpenUserProfile(String uidActual, String uidObjetivo) {
        if (TextUtils.isEmpty(uidActual) || TextUtils.isEmpty(uidObjetivo)) {
            return Tasks.forResult(false);
        }
        if (TextUtils.equals(uidActual, uidObjetivo)) {
            return Tasks.forResult(true);
        }

        Task<DocumentSnapshot> tareaPerfil = FirebaseFirestore.getInstance()
                .collection(COLECCION_USUARIO)
                .document(uidObjetivo)
                .get();
        Task<Boolean> tareaBloqueadoPorMi = isUserBlockedBy(uidActual, uidObjetivo);
        Task<Boolean> tareaBloqueadoPorObjetivo = isUserBlockedBy(uidObjetivo, uidActual);

        return Tasks.whenAllSuccess(tareaPerfil, tareaBloqueadoPorMi, tareaBloqueadoPorObjetivo)
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null || task.getResult().size() < 3) {
                        throw getTaskError(task, "No se pudo validar el acceso al perfil.");
                    }

                    Object objPerfil = task.getResult().get(0);
                    Object objBloqueadoPorMi = task.getResult().get(1);
                    Object objBloqueadoPorObjetivo = task.getResult().get(2);

                    if (!(objPerfil instanceof DocumentSnapshot)) {
                        return false;
                    }

                    DocumentSnapshot perfil = (DocumentSnapshot) objPerfil;
                    if (!perfil.exists()) {
                        return false;
                    }

                    boolean bloqueadoPorMi = Boolean.TRUE.equals(objBloqueadoPorMi);
                    boolean meBloqueo = Boolean.TRUE.equals(objBloqueadoPorObjetivo);
                    return !bloqueadoPorMi && !meBloqueo;
                });
    }

    // Limpia notificaciones, solicitudes y guardados luego de guardar el bloqueo.
    private static Task<Void> runBlockCleanupTasks(String uidBloqueador, String uidBloqueado) {
        Task<Void> tareaNotificaciones = NotificationRepository.deleteNotificationsByActorUid(uidBloqueador, uidBloqueado)
                .continueWithTask(task -> task.isSuccessful() ? Tasks.forResult(null) : logCleanupError("notificaciones", task.getException()));
        Task<Void> tareaNotificacionesInversas = NotificationRepository.deleteNotificationsByActorUid(uidBloqueado, uidBloqueador)
                .continueWithTask(task -> task.isSuccessful() ? Tasks.forResult(null) : logCleanupError("notificaciones inversas", task.getException()));
        Task<Void> tareaSolicitudes = deleteFriendRequestPair(uidBloqueador, uidBloqueado);
        Task<Void> tareaContenido = deleteUserContentPair(uidBloqueador, uidBloqueado);
        Task<Void> tareaGuardados = deleteSavedListsFromBlockedUser(uidBloqueador, uidBloqueado);

        return Tasks.whenAllComplete(tareaNotificaciones, tareaNotificacionesInversas, tareaSolicitudes, tareaContenido, tareaGuardados)
                .continueWith(task -> null);
    }

    // Elimina solicitudes de amistad de ambos lados sin revertir el bloqueo.
    private static Task<Void> deleteFriendRequestPair(String uidBloqueador, String uidBloqueado) {
        return FirebaseFirestore.getInstance()
                .collection(COLECCION_USUARIO)
                .document(uidBloqueador)
                .collection(SUBCOLECCION_SOLICITUDES)
                .document(uidBloqueado)
                .delete()
                .continueWithTask(task -> FirebaseFirestore.getInstance()
                        .collection(COLECCION_USUARIO)
                        .document(uidBloqueado)
                        .collection(SUBCOLECCION_SOLICITUDES)
                        .document(uidBloqueador)
                        .delete())
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        return logCleanupError("solicitudes", task.getException());
                    }
                    return Tasks.forResult(null);
                });
    }

    // Elimina contenido social de ambos usuarios sin fallar el bloqueo principal.
    private static Task<Void> deleteUserContentPair(String uidBloqueador, String uidBloqueado) {
        Task<Void> tareaBloqueador = deleteUserContentFromActivities(uidBloqueador, uidBloqueado)
                .continueWithTask(task -> task.isSuccessful() ? Tasks.forResult(null) : logCleanupError("contenido social", task.getException()));
        Task<Void> tareaBloqueado = deleteUserContentFromActivities(uidBloqueado, uidBloqueador)
                .continueWithTask(task -> task.isSuccessful() ? Tasks.forResult(null) : logCleanupError("contenido social", task.getException()));

        return Tasks.whenAllComplete(tareaBloqueador, tareaBloqueado).continueWith(task -> null);
    }

    // Elimina likes y comentarios del usuario objetivo en las actividades de un dueño.
    private static Task<Void> deleteUserContentFromActivities(String uidPropietario, String uidObjetivo) {
        if (TextUtils.isEmpty(uidPropietario) || TextUtils.isEmpty(uidObjetivo)) {
            return Tasks.forResult(null);
        }

        return FirebaseFirestore.getInstance()
                .collection("actividades")
                .whereEqualTo("UserID", uidPropietario)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        return logCleanupError("actividades", task.getException());
                    }

                    List<Task<Void>> tareasLimpieza = new ArrayList<>();
                    for (DocumentSnapshot actividadDoc : task.getResult().getDocuments()) {
                        tareasLimpieza.add(cleanActivityContentForUser(actividadDoc, uidObjetivo));
                    }

                    if (tareasLimpieza.isEmpty()) {
                        return Tasks.forResult(null);
                    }

                    return Tasks.whenAllComplete(tareasLimpieza).continueWith(unused -> null);
                });
    }

    // Limpia likes y comentarios de una actividad para un usuario especifico.
    private static Task<Void> cleanActivityContentForUser(DocumentSnapshot actividadDoc, String uidObjetivo) {
        if (actividadDoc == null || !actividadDoc.exists() || TextUtils.isEmpty(uidObjetivo)) {
            return Tasks.forResult(null);
        }

        DocumentReference actividadRef = actividadDoc.getReference();
        String actividadId = actividadDoc.getId();

        return actividadRef.collection("likes")
                .whereEqualTo("UserID", uidObjetivo)
                .get()
                .continueWithTask(taskLikes -> {
                    if (!taskLikes.isSuccessful() || taskLikes.getResult() == null) {
                        return logCleanupError("likes de actividad", taskLikes.getException());
                    }

                    List<Task<Void>> tareasInternas = new ArrayList<>();
                    int cantidadLikes = 0;
                    WriteBatch batchLikes = FirebaseFirestore.getInstance().batch();
                    for (DocumentSnapshot likeDoc : taskLikes.getResult().getDocuments()) {
                        batchLikes.delete(likeDoc.getReference());
                        cantidadLikes++;
                    }

                    if (cantidadLikes > 0) {
                        batchLikes.update(actividadRef, "CantidadLikes", FieldValue.increment(-cantidadLikes));
                        tareasInternas.add(batchLikes.commit());
                        tareasInternas.add(NotificationRepository.deleteNotificationsByMetadata(
                                safeString(actividadDoc.getString("UserID")),
                                NotificationType.ACTIVITY_LIKE,
                                "activityId",
                                actividadId,
                                uidObjetivo
                        ));
                    }

                    return actividadRef.collection("comentarios")
                            .whereEqualTo("UserID", uidObjetivo)
                            .get()
                            .continueWithTask(taskComentarios -> {
                                if (!taskComentarios.isSuccessful() || taskComentarios.getResult() == null) {
                                    return logCleanupError("comentarios de actividad", taskComentarios.getException());
                                }

                                int cantidadComentarios = 0;
                                WriteBatch batchComentarios = FirebaseFirestore.getInstance().batch();
                                for (DocumentSnapshot comentarioDoc : taskComentarios.getResult().getDocuments()) {
                                    batchComentarios.delete(comentarioDoc.getReference());
                                    cantidadComentarios++;
                                }

                                if (cantidadComentarios > 0) {
                                    batchComentarios.update(actividadRef, "CantidadComentarios", FieldValue.increment(-cantidadComentarios));
                                    tareasInternas.add(batchComentarios.commit());
                                    tareasInternas.add(NotificationRepository.deleteNotificationsByMetadata(
                                            safeString(actividadDoc.getString("UserID")),
                                            NotificationType.ACTIVITY_COMMENT,
                                            "activityId",
                                            actividadId,
                                            uidObjetivo
                                    ));
                                }

                                if (tareasInternas.isEmpty()) {
                                    return Tasks.forResult(null);
                                }

                                return Tasks.whenAllComplete(tareasInternas).continueWith(unused -> null);
                            });
                });
    }

    // Elimina listas guardadas del usuario bloqueado creadas por quien bloquea.
    private static Task<Void> deleteSavedListsFromBlockedUser(String uidBloqueador, String uidBloqueado) {
        return FirebaseFirestore.getInstance()
                .collection(COLECCION_LISTAS_TEMATICAS)
                .whereEqualTo("UserID", uidBloqueador)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        return logCleanupError("listas guardadas", task.getException());
                    }

                    QuerySnapshot snapshot = task.getResult();
                    if (snapshot.isEmpty()) {
                        return Tasks.forResult(null);
                    }

                    WriteBatch batch = FirebaseFirestore.getInstance().batch();
                    for (DocumentSnapshot listaDoc : snapshot.getDocuments()) {
                        batch.delete(FirebaseFirestore.getInstance()
                                .collection(COLECCION_USUARIO)
                                .document(uidBloqueado)
                                .collection(SUBCOLECCION_LISTAS_GUARDADAS)
                                .document(listaDoc.getId()));
                    }
                    return batch.commit();
                })
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        return logCleanupError("listas guardadas", task.getException());
                    }
                    return Tasks.forResult(null);
                });
    }

    // Crea la estructura basica del documento de bloqueo.
    private static Map<String, Object> createBlockedUserData(String uidBloqueado) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("UserID", uidBloqueado);
        datos.put("fechaBloqueo", Timestamp.now());
        return datos;
    }

    // Registra un error de limpieza sin romper el bloqueo principal.
    private static Task<Void> logCleanupError(String nombreOperacion, Exception error) {
        Log.w(TAG, "No se pudo limpiar " + nombreOperacion + ": " + (error != null ? error.getMessage() : ""));
        return Tasks.forResult(null);
    }

    // Verifica si uidObjetivo tiene bloqueado a uidConsultado.
    public static Task<Boolean> isUserBlockedBy(String uidConsultado, String uidObjetivo) {
        if (TextUtils.isEmpty(uidConsultado) || TextUtils.isEmpty(uidObjetivo)) {
            return Tasks.forResult(false);
        }

        return FirebaseFirestore.getInstance()
                .collection(COLECCION_USUARIO)
                .document(uidObjetivo)
                .collection(SUBCOLECCION_BLOQUEADOS)
                .document(uidConsultado)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw getTaskError(task, "No se pudo validar bloqueo.");
                    }
                    DocumentSnapshot documento = task.getResult();
                    return documento != null && documento.exists();
                });
    }

    // Devuelve los UIDs que el usuario actual tiene bloqueados.
    public static Task<List<String>> getBlockedUserIds(String uidActual) {
        if (TextUtils.isEmpty(uidActual)) {
            return Tasks.forResult(new ArrayList<>());
        }

        return FirebaseFirestore.getInstance()
                .collection(COLECCION_USUARIO)
                .document(uidActual)
                .collection(SUBCOLECCION_BLOQUEADOS)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw getTaskError(task, "No se pudo cargar usuarios bloqueados.");
                    }

                    List<String> bloqueados = new ArrayList<>();
                    for (DocumentSnapshot documento : task.getResult().getDocuments()) {
                        String uid = safeString((String) documento.get("UserID"));
                        if (TextUtils.isEmpty(uid)) {
                            uid = documento.getId();
                        }
                        if (!TextUtils.isEmpty(uid)) {
                            bloqueados.add(uid);
                        }
                    }
                    return bloqueados;
                });
    }

    // Devuelve los UIDs de usuarios que bloquearon al usuario actual.
    public static Task<List<String>> getUsersWhoBlockedUserIds(String uidActual) {
        if (TextUtils.isEmpty(uidActual)) {
            return Tasks.forResult(new ArrayList<>());
        }

        return FirebaseFirestore.getInstance()
                .collection(COLECCION_USUARIO)
                .get()
                .continueWithTask(taskUsuarios -> {
                    if (!taskUsuarios.isSuccessful() || taskUsuarios.getResult() == null) {
                        throw getTaskError(taskUsuarios, "No se pudo cargar usuarios para validar bloqueos.");
                    }

                    List<Task<DocumentSnapshot>> tareasBloqueo = new ArrayList<>();
                    List<String> uidPropietarios = new ArrayList<>();
                    for (DocumentSnapshot usuarioDoc : taskUsuarios.getResult().getDocuments()) {
                        String uidPropietario = usuarioDoc.getId();
                        if (TextUtils.isEmpty(uidPropietario) || TextUtils.equals(uidPropietario, uidActual)) {
                            continue;
                        }
                        uidPropietarios.add(uidPropietario);
                        tareasBloqueo.add(FirebaseFirestore.getInstance()
                                .collection(COLECCION_USUARIO)
                                .document(uidPropietario)
                                .collection(SUBCOLECCION_BLOQUEADOS)
                                .document(uidActual)
                                .get());
                    }

                    if (tareasBloqueo.isEmpty()) {
                        return Tasks.forResult(new ArrayList<>());
                    }

                    return Tasks.whenAllSuccess(tareasBloqueo).continueWith(taskBloqueos -> {
                        if (!taskBloqueos.isSuccessful() || taskBloqueos.getResult() == null) {
                            throw getTaskError(taskBloqueos, "No se pudo completar validacion de bloqueos.");
                        }

                        List<String> bloqueadores = new ArrayList<>();
                        List<?> resultados = taskBloqueos.getResult();
                        for (int i = 0; i < resultados.size() && i < uidPropietarios.size(); i++) {
                            Object item = resultados.get(i);
                            if (!(item instanceof DocumentSnapshot)) {
                                continue;
                            }
                            DocumentSnapshot documentoBloqueo = (DocumentSnapshot) item;
                            if (documentoBloqueo.exists()) {
                                bloqueadores.add(uidPropietarios.get(i));
                            }
                        }
                        return bloqueadores;
                    });
                });
    }

    // Convierte FotoPerfil a dataUrl de forma segura.
    private static String extractPhotoDataUrl(Object fotoPerfil) {
        if (fotoPerfil instanceof String) {
            return String.valueOf(fotoPerfil);
        }
        if (!(fotoPerfil instanceof Map)) {
            return "";
        }
        Map<?, ?> mapa = (Map<?, ?>) fotoPerfil;
        Object dataUrl = mapa.get("dataUrl");
        return dataUrl == null ? "" : String.valueOf(dataUrl);
    }

    // Devuelve string vacio cuando el valor es nulo.
    private static String safeString(String valor) {
        return valor == null ? "" : valor;
    }

    // Devuelve un error claro para fallas de tareas.
    private static Exception getTaskError(Task<?> tarea, String mensaje) {
        Exception error = tarea.getException();
        return error != null ? error : new Exception(mensaje);
    }
}
