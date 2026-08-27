package com.example.comiku.core.validation;

import java.util.List;
import java.util.regex.Pattern;

public final class InputValidator {
    private static final Pattern PATRON_CARACTERES_PROHIBIDOS = Pattern.compile("[@#$^&*{}\\[\\]<>]");

    private InputValidator() {
    }

    // Verifica si un texto tiene caracteres no permitidos.
    public static boolean hasForbiddenChars(String valor) {
        return PATRON_CARACTERES_PROHIBIDOS.matcher(String.valueOf(valor)).find();
    }

    // Limpia los caracteres no permitidos de un texto.
    public static String sanitizeForbiddenChars(String valor) {
        return PATRON_CARACTERES_PROHIBIDOS.matcher(String.valueOf(valor)).replaceAll("");
    }

    // Verifica si algun texto de una lista tematica tiene caracteres no permitidos.
    public static boolean hasForbiddenCharsInList(List<String> valores) {
        if (valores == null || valores.isEmpty()) {
            return false;
        }

        for (String valor : valores) {
            if (hasForbiddenChars(valor)) {
                return true;
            }
        }
        return false;
    }
}
