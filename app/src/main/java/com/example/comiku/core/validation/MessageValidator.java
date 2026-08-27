package com.example.comiku.core.validation;

import java.util.regex.Pattern;

// Validador para mensajes con reglas de longitud y caracteres prohibidos
public class MessageValidator {
    private static final int MAX_LENGTH = 5000;
    private static final int MIN_LENGTH = 1;
    private static final String CARACTERES_PROHIBIDOS = "@#$^&*{}[]<>";
    private static final Pattern PATTERN_CARACTERES_PROHIBIDOS = 
        Pattern.compile("[" + Pattern.quote(CARACTERES_PROHIBIDOS) + "]");

    public static class ValidationResult {
        public final boolean valido;
        public final String mensajeError;

        public ValidationResult(boolean valido, String mensajeError) {
            this.valido = valido;
            this.mensajeError = mensajeError;
        }

        public static ValidationResult OK() {
            return new ValidationResult(true, "");
        }

        public static ValidationResult ERROR(String mensaje) {
            return new ValidationResult(false, mensaje);
        }
    }

    // Validar mensaje completo
    public static ValidationResult validar(String texto) {
        if (texto == null) {
            return ValidationResult.ERROR("El mensaje no puede estar vacío");
        }

        String trimmed = texto.trim();

        // Validar longitud mínima
        if (trimmed.length() < MIN_LENGTH) {
            return ValidationResult.ERROR("El mensaje no puede estar vacío");
        }

        // Validar longitud máxima
        if (trimmed.length() > MAX_LENGTH) {
            return ValidationResult.ERROR("El mensaje excede " + MAX_LENGTH + " caracteres");
        }

        // Validar caracteres prohibidos
        ValidationResult resultadoCaracteres = validarCaracteresProhibidos(trimmed);
        if (!resultadoCaracteres.valido) {
            return resultadoCaracteres;
        }

        return ValidationResult.OK();
    }

    // Validar solo caracteres prohibidos
    private static ValidationResult validarCaracteresProhibidos(String texto) {
        if (PATTERN_CARACTERES_PROHIBIDOS.matcher(texto).find()) {
            return ValidationResult.ERROR(
                "No se permiten caracteres especiales: " + CARACTERES_PROHIBIDOS
            );
        }
        return ValidationResult.OK();
    }

    // Obtener los caracteres prohibidos
    public static String obtenerCaracteresProhibidos() {
        return CARACTERES_PROHIBIDOS;
    }

    // Obtener longitud máxima permitida
    public static int obtenerLongitudMaxima() {
        return MAX_LENGTH;
    }

    // Verificar si la longitud es válida para mostrar contador
    public static boolean esLongitudValida(String texto) {
        if (texto == null) return true;
        return texto.length() <= MAX_LENGTH;
    }

    // Calcular el porcentaje de uso
    public static int calcularPorcentajeUso(String texto) {
        if (texto == null) return 0;
        return (texto.length() * 100) / MAX_LENGTH;
    }
}
