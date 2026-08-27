package com.example.comiku.data.repository;

import android.text.TextUtils;

import com.example.comiku.core.validation.InputValidator;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public final class ContactMessageRepository {
    public static final String TIPO_SUGERENCIA = "Sugerencia";
    public static final String TIPO_QUEJA = "Queja";
    public static final String TIPO_OTROS = "Otros";

    private static final int LONGITUD_MINIMA_MENSAJE = 10;
    private static final String COLECCION_MENSAJES_USUARIOS = "mensajesUsuarios";

    private ContactMessageRepository() {
    }

    // Devuelve los tipos validos para el formulario de contacto.
    public static String[] getMessageTypes() {
        return new String[]{TIPO_SUGERENCIA, TIPO_QUEJA, TIPO_OTROS};
    }

    // Guarda un mensaje del usuario para la administracion.
    public static Task<String> createMessage(String tipo, String descripcion, String usuarioId) {
        String tipoLimpio = sanitize(tipo);
        String descripcionLimpia = sanitize(descripcion);
        String usuarioIdLimpio = sanitize(usuarioId);

        String errorValidacion = validateMessage(tipoLimpio, descripcionLimpia, usuarioIdLimpio);
        if (!TextUtils.isEmpty(errorValidacion)) {
            return Tasks.forException(new IllegalArgumentException(errorValidacion));
        }

        Map<String, Object> datos = new HashMap<>();
        datos.put("TipoMensaje", tipoLimpio);
        datos.put("Descripcion", descripcionLimpia);
        datos.put("UserID", usuarioIdLimpio);
        datos.put("Fecha", FieldValue.serverTimestamp());
        datos.put("Leido", false);
        datos.put("FechaLectura", null);

        return FirebaseFirestore.getInstance()
                .collection(COLECCION_MENSAJES_USUARIOS)
                .add(datos)
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw getTaskError(task, "No se pudo enviar el mensaje.");
                    }
                    return task.getResult().getId();
                });
    }

    // Valida tipo, descripcion y usuario antes de guardar.
    private static String validateMessage(String tipo, String descripcion, String usuarioId) {
        if (TextUtils.isEmpty(usuarioId)) {
            return "Usuario no autenticado.";
        }
        if (!isValidMessageType(tipo)) {
            return "Tipo de mensaje invalido.";
        }
        if (TextUtils.isEmpty(descripcion)) {
            return "El mensaje es obligatorio.";
        }
        if (descripcion.length() < LONGITUD_MINIMA_MENSAJE) {
            return "El mensaje debe tener al menos 10 caracteres.";
        }
        return "";
    }

    // Verifica si el tipo elegido esta permitido.
    private static boolean isValidMessageType(String tipo) {
        return TIPO_SUGERENCIA.equals(tipo)
                || TIPO_QUEJA.equals(tipo)
                || TIPO_OTROS.equals(tipo);
    }

    // Limpia caracteres no permitidos para entradas del usuario.
    private static String sanitize(String valor) {
        if (valor == null) {
            return "";
        }
        return InputValidator.sanitizeForbiddenChars(valor).trim();
    }

    // Devuelve un error claro para tareas fallidas.
    private static Exception getTaskError(Task<?> tarea, String mensaje) {
        Exception error = tarea.getException();
        return error != null ? error : new Exception(mensaje);
    }
}
