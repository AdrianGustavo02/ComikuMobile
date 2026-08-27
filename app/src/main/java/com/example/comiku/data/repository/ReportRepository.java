package com.example.comiku.data.repository;

import android.text.TextUtils;

import com.example.comiku.core.validation.InputValidator;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class ReportRepository {
    private static final String COLECCION_REPORTES = "Reportes";
    private static final String ESTADO_PENDIENTE = "pendiente";
    private static final String OBJETO_COMIC = "comic";
    private static final String OBJETO_TOMO = "tomo";
    private static final String OBJETO_USUARIO = "usuario";
    private static final String OBJETO_GRUPO = "grupo de chat";
    private static final String MOTIVO_USUARIO_COMPORTAMIENTO = "Comportamiento inapropiado";
    private static final String MOTIVO_USUARIO_SPAM = "Spam";
    private static final String MOTIVO_GRUPO_CONTENIDO = "Contenido inapropiado";
    private static final String MOTIVO_GRUPO_SPAM = "Spam";

    private ReportRepository() {
    }

    // Crea un reporte validando duplicados pendientes por usuario, objeto y motivo.
    public static Task<String> createContentReport(
            String usuarioId,
            String objetoReportadoId,
            String nombreObjetoReportado,
            String comicId,
            String motivo,
            String descripcion,
            Map<String, Object> capturaPantalla
    ) {
        String usuarioLimpio = sanitize(usuarioId);
        String objetoLimpio = sanitize(objetoReportadoId);
        String objetoNombreLimpio = sanitize(nombreObjetoReportado).toLowerCase(Locale.ROOT);
        String comicIdLimpio = sanitize(comicId);
        String motivoLimpio = sanitize(motivo);
        String descripcionLimpia = sanitize(descripcion);

        String errorValidacion = validateReportPayload(
                usuarioLimpio,
                objetoLimpio,
                objetoNombreLimpio,
                comicIdLimpio,
                motivoLimpio,
                descripcionLimpia
        );
        if (!TextUtils.isEmpty(errorValidacion)) {
            return Tasks.forException(new IllegalArgumentException(errorValidacion));
        }

        Query consultaDuplicado = FirebaseFirestore.getInstance()
                .collection(COLECCION_REPORTES)
                .whereEqualTo("UserID", usuarioLimpio)
                .whereEqualTo("NombreObjetoReportado", objetoNombreLimpio)
                .whereEqualTo("ObjetoReportadoID", objetoLimpio)
                .whereEqualTo("Motivo", motivoLimpio)
                .whereEqualTo("Estado", ESTADO_PENDIENTE)
                .limit(1);

        if (OBJETO_TOMO.equals(objetoNombreLimpio)) {
            consultaDuplicado = consultaDuplicado.whereEqualTo("ComicId", comicIdLimpio);
        }

        String claveReporte = buildReportKey(
                usuarioLimpio,
                objetoLimpio,
                objetoNombreLimpio,
                comicIdLimpio,
                motivoLimpio
        );

        return consultaDuplicado.get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw getTaskError(task, "No se pudo validar el reporte duplicado.");
                    }
                    QuerySnapshot snapshot = task.getResult();
                    if (snapshot != null && !snapshot.isEmpty()) {
                        String mensajeDuplicado = OBJETO_TOMO.equals(objetoNombreLimpio)
                                ? "Ya tienes un reporte pendiente de este tipo para este tomo."
                                : "Ya tienes un reporte pendiente de este tipo para este comic.";
                        throw new IllegalStateException(mensajeDuplicado);
                    }

                    Map<String, Object> datos = new HashMap<>();
                    datos.put("UserID", usuarioLimpio);
                    datos.put("ObjetoReportadoID", objetoLimpio);
                    datos.put("NombreObjetoReportado", objetoNombreLimpio);
                    datos.put("ClaveReporte", claveReporte);
                    datos.put("Motivo", motivoLimpio);
                    datos.put("Descripcion", descripcionLimpia);
                    datos.put("Estado", ESTADO_PENDIENTE);
                    datos.put("FechaReporte", FieldValue.serverTimestamp());

                    if (capturaPantalla != null && !capturaPantalla.isEmpty()) {
                        datos.put("CapturaPantalla", capturaPantalla);
                    }

                    if (OBJETO_TOMO.equals(objetoNombreLimpio)) {
                        datos.put("ComicId", comicIdLimpio);
                    }

                    return FirebaseFirestore.getInstance()
                            .collection(COLECCION_REPORTES)
                            .add(datos)
                            .continueWith(tareaGuardar -> {
                                if (!tareaGuardar.isSuccessful() || tareaGuardar.getResult() == null) {
                                    throw getTaskError(tareaGuardar, "No se pudo enviar el reporte.");
                                }
                                return tareaGuardar.getResult().getId();
                            });
                });
    }

    // Valida campos obligatorios y tipo de objeto reportado.
    private static String validateReportPayload(
            String usuarioId,
            String objetoReportadoId,
            String nombreObjetoReportado,
            String comicId,
            String motivo,
            String descripcion
    ) {
        if (TextUtils.isEmpty(usuarioId)) {
            return "No se pudo crear el reporte: usuario invalido.";
        }
        if (TextUtils.isEmpty(objetoReportadoId)) {
            return "No se pudo crear el reporte: objeto invalido.";
        }
        if (!OBJETO_COMIC.equals(nombreObjetoReportado) && !OBJETO_TOMO.equals(nombreObjetoReportado)) {
            return "Tipo de objeto reportado invalido.";
        }
        if (OBJETO_TOMO.equals(nombreObjetoReportado) && TextUtils.isEmpty(comicId)) {
            return "No se pudo crear el reporte: comic padre invalido.";
        }
        if (TextUtils.isEmpty(motivo)) {
            return "Debes seleccionar un motivo.";
        }
        if (TextUtils.isEmpty(descripcion)) {
            return "La descripcion del reporte es obligatoria.";
        }
        return "";
    }

    // Construye una clave de reporte para facilitar trazabilidad.
    private static String buildReportKey(
            String usuarioId,
            String objetoReportadoId,
            String nombreObjetoReportado,
            String comicId,
            String motivo
    ) {
        String objetoClave = OBJETO_TOMO.equals(nombreObjetoReportado)
                ? comicId + "::" + objetoReportadoId
                : objetoReportadoId;
        return usuarioId + "::" + nombreObjetoReportado + "::" + objetoClave + "::" + motivo.toLowerCase(Locale.ROOT);
    }

    // Limpia caracteres prohibidos en texto.
    private static String sanitize(String valor) {
        return InputValidator.sanitizeForbiddenChars(String.valueOf(valor)).trim();
    }

    // Devuelve error claro para tareas fallidas.
    private static Exception getTaskError(Task<?> tarea, String mensaje) {
        Exception error = tarea.getException();
        return error != null ? error : new Exception(mensaje);
    }

    // Crea un reporte de usuario validando que solo reporte una vez.
    public static Task<String> createUserReport(
            String reportadorUid,
            String usuarioReportadoUid,
            String usuarioReportadoNick,
            String motivo,
            String descripcion,
            Map<String, Object> capturaPantalla
    ) {
        String reportadorLimpio = sanitize(reportadorUid);
        String usuarioReportadoLimpio = sanitize(usuarioReportadoUid);
        String nickLimpio = sanitize(usuarioReportadoNick).toLowerCase(Locale.ROOT);
        String motivoLimpio = sanitize(motivo);
        String descripcionLimpia = sanitize(descripcion);

        String errorValidacion = validateUserReportPayload(
                reportadorLimpio,
                usuarioReportadoLimpio,
                nickLimpio,
                motivoLimpio,
                descripcionLimpia
        );
        if (!TextUtils.isEmpty(errorValidacion)) {
            return Tasks.forException(new IllegalArgumentException(errorValidacion));
        }

        Query consultaDuplicado = FirebaseFirestore.getInstance()
                .collection(COLECCION_REPORTES)
                .whereEqualTo("UserID", reportadorLimpio)
                .whereEqualTo("ObjetoReportadoID", usuarioReportadoLimpio)
                .whereEqualTo("NombreObjetoReportado", OBJETO_USUARIO)
                .whereEqualTo("Estado", ESTADO_PENDIENTE)
                .limit(1);

        String claveReporte = buildUserReportKey(reportadorLimpio, usuarioReportadoLimpio, motivoLimpio);

        return consultaDuplicado.get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw getTaskError(task, "No se pudo validar el reporte duplicado.");
                    }
                    QuerySnapshot snapshot = task.getResult();
                    if (snapshot != null && !snapshot.isEmpty()) {
                        throw new IllegalStateException("Ya tienes un reporte pendiente para este usuario.");
                    }

                    Map<String, Object> datos = new HashMap<>();
                    datos.put("UserID", reportadorLimpio);
                    datos.put("ObjetoReportadoID", usuarioReportadoLimpio);
                    datos.put("NombreObjetoReportado", OBJETO_USUARIO);
                    datos.put("UsuarioReportadoNick", nickLimpio);
                    datos.put("ClaveReporte", claveReporte);
                    datos.put("Motivo", motivoLimpio);
                    datos.put("Descripcion", descripcionLimpia);
                    datos.put("Estado", ESTADO_PENDIENTE);
                    datos.put("FechaReporte", FieldValue.serverTimestamp());
                    if (capturaPantalla != null && !capturaPantalla.isEmpty()) {
                        datos.put("CapturaPantalla", capturaPantalla);
                    }

                    return FirebaseFirestore.getInstance()
                            .collection(COLECCION_REPORTES)
                            .add(datos)
                            .continueWith(tareaGuardar -> {
                                if (!tareaGuardar.isSuccessful() || tareaGuardar.getResult() == null) {
                                    throw getTaskError(tareaGuardar, "No se pudo enviar el reporte.");
                                }
                                return tareaGuardar.getResult().getId();
                            });
                });
    }

    // Valida campos obligatorios para reporte de usuario.
    private static String validateUserReportPayload(
            String reportadorUid,
            String usuarioReportadoUid,
            String nickReportado,
            String motivo,
            String descripcion
    ) {
        if (TextUtils.isEmpty(reportadorUid)) {
            return "No se pudo crear el reporte: reportador invalido.";
        }
        if (TextUtils.isEmpty(usuarioReportadoUid)) {
            return "No se pudo crear el reporte: usuario invalido.";
        }
        if (reportadorUid.equals(usuarioReportadoUid)) {
            return "No puedes reportarte a ti mismo.";
        }
        if (TextUtils.isEmpty(nickReportado)) {
            return "No se pudo crear el reporte: nick del usuario invalido.";
        }
        if (TextUtils.isEmpty(motivo)) {
            return "Debes seleccionar un motivo.";
        }
        if (!MOTIVO_USUARIO_COMPORTAMIENTO.equals(motivo) && !MOTIVO_USUARIO_SPAM.equals(motivo)) {
            return "Selecciona un motivo valido para reportar este usuario.";
        }
        if (TextUtils.isEmpty(descripcion)) {
            return "La descripcion del reporte es obligatoria.";
        }
        return "";
    }

    // Construye clave de reporte de usuario para trazabilidad.
    private static String buildUserReportKey(
            String reportadorUid,
            String usuarioReportadoUid,
            String motivo
    ) {
        return reportadorUid + "::" + usuarioReportadoUid + "::" + motivo.toLowerCase(Locale.ROOT);
    }

    // Crea un reporte de grupo validando que solo miembros reporten una vez.
    public static Task<String> createGroupReport(
            String reportadorUid,
            String grupoId,
            String grupoNombre,
            String motivo,
            String descripcion,
            Map<String, Object> capturaPantalla
    ) {
        String reportadorLimpio = sanitize(reportadorUid);
        String grupoIdLimpio = sanitize(grupoId);
        String motivoLimpio = sanitize(motivo);
        String descripcionLimpia = sanitize(descripcion);
        String nombreGrupoLimpio = sanitize(grupoNombre);

        String errorValidacion = validateGroupReportPayload(
                reportadorLimpio,
                grupoIdLimpio,
                motivoLimpio,
                descripcionLimpia
        );
        if (!TextUtils.isEmpty(errorValidacion)) {
            return Tasks.forException(new IllegalArgumentException(errorValidacion));
        }

        String claveReporte = buildGroupReportKey(reportadorLimpio, grupoIdLimpio);
        Query consultaDuplicado = FirebaseFirestore.getInstance()
                .collection(COLECCION_REPORTES)
                .whereEqualTo("ClaveReporte", claveReporte)
                .whereEqualTo("Estado", ESTADO_PENDIENTE)
                .limit(1);

        return consultaDuplicado.get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw getTaskError(task, "No se pudo validar el reporte duplicado.");
                    }
                    QuerySnapshot snapshot = task.getResult();
                    if (snapshot != null && !snapshot.isEmpty()) {
                        throw new IllegalStateException("Ya tienes un reporte pendiente para este grupo.");
                    }

                    Map<String, Object> datos = new HashMap<>();
                    datos.put("UserID", reportadorLimpio);
                    datos.put("ObjetoReportadoID", grupoIdLimpio);
                    datos.put("NombreObjetoReportado", OBJETO_GRUPO);
                    datos.put("ClaveReporte", claveReporte);
                    datos.put("Motivo", motivoLimpio);
                    datos.put("Descripcion", descripcionLimpia);
                    datos.put("Estado", ESTADO_PENDIENTE);
                    datos.put("FechaReporte", FieldValue.serverTimestamp());
                    if (!TextUtils.isEmpty(nombreGrupoLimpio)) {
                        datos.put("GrupoNombre", nombreGrupoLimpio);
                    }

                    if (capturaPantalla != null && !capturaPantalla.isEmpty()) {
                        datos.put("CapturaPantalla", capturaPantalla);
                    }

                    return FirebaseFirestore.getInstance()
                            .collection(COLECCION_REPORTES)
                            .add(datos)
                            .continueWith(tareaGuardar -> {
                                if (!tareaGuardar.isSuccessful() || tareaGuardar.getResult() == null) {
                                    throw getTaskError(tareaGuardar, "No se pudo enviar el reporte.");
                                }
                                return tareaGuardar.getResult().getId();
                            });
                });
    }

    // Valida campos obligatorios para reporte de grupo.
    private static String validateGroupReportPayload(
            String reportadorUid,
            String grupoId,
            String motivo,
            String descripcion
    ) {
        if (TextUtils.isEmpty(reportadorUid)) {
            return "No se pudo crear el reporte: reportador invalido.";
        }
        if (TextUtils.isEmpty(grupoId)) {
            return "No se pudo crear el reporte: grupo invalido.";
        }
        if (TextUtils.isEmpty(motivo)) {
            return "Debes seleccionar un motivo.";
        }
        if (!MOTIVO_GRUPO_CONTENIDO.equals(motivo) && !MOTIVO_GRUPO_SPAM.equals(motivo)) {
            return "Selecciona un motivo valido para reportar este grupo de chat.";
        }
        if (TextUtils.isEmpty(descripcion)) {
            return "La descripcion del reporte es obligatoria.";
        }
        return "";
    }

    // Construye clave de reporte de grupo para trazabilidad.
    private static String buildGroupReportKey(
            String reportadorUid,
            String grupoId
    ) {
        return reportadorUid + "::" + OBJETO_GRUPO + "::" + grupoId;
    }
}