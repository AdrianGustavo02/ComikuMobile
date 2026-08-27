package com.example.comiku.core.validation;

import java.util.List;

// Validador para creacion y edicion de grupos de chat
public class GroupValidator {
    private static final int NOMBRE_MIN = 3;
    private static final int NOMBRE_MAX = 100;
    private static final int DESCRIPCION_MAX = 1000;
    private static final String CARACTERES_PROHIBIDOS = "@#$^&*{}[]<>";

    // Resultado de validacion del grupo
    public static class ValidationResult {
        public final boolean valido;
        public final String error;

        public ValidationResult(boolean valido, String error) {
            this.valido = valido;
            this.error = error;
        }
    }

    // Validar datos para crear grupo
    public static ValidationResult validarCreacionGrupo(String nombreGrupo, List<String> miembrosSeleccionados) {
        ValidationResult resultadoNombre = validarNombreGrupo(nombreGrupo);
        if (!resultadoNombre.valido) {
            return resultadoNombre;
        }

        if (miembrosSeleccionados == null || miembrosSeleccionados.size() < 2) {
            return new ValidationResult(false, "Debes seleccionar al menos dos amigos");
        }

        return new ValidationResult(true, "");
    }

    // Validar nombre de grupo
    public static ValidationResult validarNombreGrupo(String nombreGrupo) {
        if (nombreGrupo == null) {
            return new ValidationResult(false, "El nombre del grupo es obligatorio");
        }

        String nombreLimpio = nombreGrupo.trim();
        if (nombreLimpio.isEmpty()) {
            return new ValidationResult(false, "El nombre del grupo es obligatorio");
        }

        if (nombreLimpio.length() < NOMBRE_MIN) {
            return new ValidationResult(false, "El nombre debe tener al menos " + NOMBRE_MIN + " caracteres");
        }

        if (nombreLimpio.length() > NOMBRE_MAX) {
            return new ValidationResult(false, "El nombre no puede superar " + NOMBRE_MAX + " caracteres");
        }

        if (contieneCaracteresProhibidos(nombreLimpio)) {
            return new ValidationResult(false, "El nombre contiene caracteres no permitidos");
        }

        return new ValidationResult(true, "");
    }

    // Validar descripcion del grupo
    public static ValidationResult validarDescripcionGrupo(String descripcionGrupo) {
        if (descripcionGrupo == null || descripcionGrupo.trim().isEmpty()) {
            return new ValidationResult(true, "");
        }

        String descripcionLimpia = descripcionGrupo.trim();
        if (descripcionLimpia.length() > DESCRIPCION_MAX) {
            return new ValidationResult(false, "La descripcion no puede superar " + DESCRIPCION_MAX + " caracteres");
        }

        if (contieneCaracteresProhibidos(descripcionLimpia)) {
            return new ValidationResult(false, "La descripcion contiene caracteres no permitidos");
        }

        return new ValidationResult(true, "");
    }

    // Verificar caracteres no permitidos
    private static boolean contieneCaracteresProhibidos(String texto) {
        for (int i = 0; i < CARACTERES_PROHIBIDOS.length(); i++) {
            if (texto.indexOf(CARACTERES_PROHIBIDOS.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }
}
