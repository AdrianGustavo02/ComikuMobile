package com.example.comiku.core.error;

import com.google.firebase.firestore.FirebaseFirestoreException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

// Traductor de errores a mensajes para el usuario
public class ErrorHandler {

    public static class ErrorInfo {
        public final String titulo;
        public final String mensaje;
        public final ErrorType tipo;
        public final boolean esReintentable;

        public ErrorInfo(String titulo, String mensaje, ErrorType tipo, boolean esReintentable) {
            this.titulo = titulo;
            this.mensaje = mensaje;
            this.tipo = tipo;
            this.esReintentable = esReintentable;
        }
    }

    public enum ErrorType {
        RED,                    // Error de conexión
        FIRESTORE,              // Error de Firestore
        VALIDACION,             // Error de validación
        PERMISO,                // Error de permiso
        USUARIO_NO_EXISTE,      // Usuario no existe
        NO_AMIGOS,              // No son amigos
        BLOQUEADO,              // Uno bloqueó al otro
        DESCONOCIDO            // Error desconocido
    }

    // Obtener información de error basada en el error
    public static ErrorInfo obtenerInfoError(Exception exception) {
        if (exception == null) {
            return new ErrorInfo(
                "Error desconocido",
                "Ocurrió un error inesperado. Intenta de nuevo.",
                ErrorType.DESCONOCIDO,
                true
            );
        }

        // Errores de red
        if (esErrorDeRed(exception)) {
            return new ErrorInfo(
                "Sin conexión",
                "Verifica tu conexión a Internet e intenta de nuevo.",
                ErrorType.RED,
                true
            );
        }

        // Errores de Firestore
        if (exception instanceof FirebaseFirestoreException) {
            return obtenerInfoErrorFirestore((FirebaseFirestoreException) exception);
        }

        // Otros errores
        String mensaje = exception.getMessage() != null ? 
            exception.getMessage() : 
            "Ocurrió un error inesperado";

        return new ErrorInfo(
            "Error",
            mensaje,
            ErrorType.DESCONOCIDO,
            true
        );
    }

    // Obtener información de errores específicos de Firestore
    private static ErrorInfo obtenerInfoErrorFirestore(FirebaseFirestoreException exception) {
        int codigo;
        try {
            codigo = exception.getCode().ordinal();
        } catch (Exception e) {
            // Si no podemos obtener el código, usar -1 como defecto
            codigo = -1;
        }

        switch (codigo) {
            case 0: // OK - No debería llegar aquí
                return new ErrorInfo(
                    "Error",
                    "Ocurrió un error inesperado",
                    ErrorType.DESCONOCIDO,
                    false
                );

            case 1: // CANCELLED
                return new ErrorInfo(
                    "Operación cancelada",
                    "La operación fue cancelada",
                    ErrorType.DESCONOCIDO,
                    true
                );

            case 3: // INVALID_ARGUMENT
                return new ErrorInfo(
                    "Mensaje inválido",
                    "El mensaje contiene caracteres no permitidos o es demasiado largo",
                    ErrorType.VALIDACION,
                    false
                );

            case 5: // NOT_FOUND
                return new ErrorInfo(
                    "Chat no encontrado",
                    "El chat fue eliminado o ya no está disponible",
                    ErrorType.USUARIO_NO_EXISTE,
                    true
                );

            case 7: // PERMISSION_DENIED
                return new ErrorInfo(
                    "Sin permiso",
                    "No tienes acceso para enviar mensajes en este chat",
                    ErrorType.PERMISO,
                    false
                );

            case 8: // RESOURCE_EXHAUSTED
                return new ErrorInfo(
                    "Demasiadas operaciones",
                    "Se alcanzó el límite. Espera unos minutos e intenta de nuevo.",
                    ErrorType.FIRESTORE,
                    true
                );

            case 9: // FAILED_PRECONDITION
                return new ErrorInfo(
                    "Condición no cumplida",
                    "No se pueden cumplir los requisitos necesarios. Intenta de nuevo.",
                    ErrorType.FIRESTORE,
                    true
                );

            case 10: // ABORTED
                return new ErrorInfo(
                    "Operación abortada",
                    "La operación fue interrumpida. Intenta de nuevo.",
                    ErrorType.FIRESTORE,
                    true
                );

            case 11: // UNAVAILABLE
                return new ErrorInfo(
                    "Servicio no disponible",
                    "Los servidores están ocupados. Intenta más tarde.",
                    ErrorType.FIRESTORE,
                    true
                );

            case 12: // DATA_LOSS
                return new ErrorInfo(
                    "Pérdida de datos",
                    "Hubo un problema grave. Intenta de nuevo.",
                    ErrorType.FIRESTORE,
                    true
                );

            case 13: // UNAUTHENTICATED
                return new ErrorInfo(
                    "No autenticado",
                    "Tu sesión expiró. Por favor, inicia sesión de nuevo.",
                    ErrorType.PERMISO,
                    false
                );

            case 14: // DEADLINE_EXCEEDED
                return new ErrorInfo(
                    "Tiempo agotado",
                    "La operación tardó demasiado. Intenta de nuevo.",
                    ErrorType.RED,
                    true
                );

            default:
                return new ErrorInfo(
                    "Error de servidor",
                    "Hubo un problema. Por favor intenta de nuevo.",
                    ErrorType.FIRESTORE,
                    true
                );
        }
    }

    // Verificar si es un error de red
    private static boolean esErrorDeRed(Exception exception) {
        return exception instanceof ConnectException ||
               exception instanceof SocketTimeoutException ||
               exception instanceof UnknownHostException ||
               exception instanceof IOException ||
               (exception.getMessage() != null && 
                (exception.getMessage().contains("network") ||
                 exception.getMessage().contains("connection") ||
                 exception.getMessage().contains("timeout")));
    }

    // Obtener icono de estado para mensaje
    public static String obtenerIconoEstado(String estado) {
        if (estado == null) return "⏳";

        switch (estado) {
            case "sending":
                return "⏳";    // Enviando
            case "sent":
                return "✓";     // Enviado
            case "delivered":
                return "✓";     // Entregado
            case "read":
                return "✓✓";    // Leído
            case "error":
                return "❌";    // Error
            default:
                return "";
        }
    }

    // Obtener color de estado para mensaje
    public static int obtenerColorEstado(String estado) {
        if (estado == null) return 0xFFB0BEC5; // Gris claro

        switch (estado) {
            case "sending":
                return 0xFFB0BEC5;  // Gris claro
            case "sent":
                return 0xFFB0BEC5;  // Gris claro
            case "delivered":
                return 0xFFB0BEC5;  // Gris claro
            case "read":
                return 0xFF2196F3;  // Azul
            case "error":
                return 0xFFF44336;  // Rojo
            default:
                return 0xFFB0BEC5;  // Gris claro
        }
    }
}
