package com.example.comiku.data.model;

import java.util.ArrayList;
import java.util.List;

public final class ComicSearchResult {
    public final String id;
    public final String nombre;
    public final List<String> autores;
    public final String editorial;
    public final String paisEditorial;
    public final String portadaDataUrl;

    // Crea un resultado de busqueda desde un documento de Firestore.
    public ComicSearchResult(
            String id,
            String nombre,
            List<String> autores,
            String editorial,
            String paisEditorial,
            String portadaDataUrl
    ) {
        this.id = id;
        this.nombre = nombre;
        this.autores = autores != null ? autores : new ArrayList<>();
        this.editorial = editorial;
        this.paisEditorial = paisEditorial;
        this.portadaDataUrl = portadaDataUrl;
    }

    // Formatea el resultado para mostrar en lista.
    public String getFormattedDisplay() {
        StringBuilder sb = new StringBuilder();
        sb.append(nombre).append("\n");

        if (!autores.isEmpty()) {
            sb.append("Autor: ").append(String.join(", ", autores)).append("\n");
        }

        if (editorial != null && !editorial.isEmpty()) {
            sb.append("Editorial: ").append(editorial);
            if (paisEditorial != null && !paisEditorial.isEmpty()) {
                sb.append(" (").append(paisEditorial).append(")");
            }
        }

        return sb.toString();
    }

    // Devuelve el texto secundario del resultado para la lista.
    public String getSecondaryDisplay() {
        StringBuilder sb = new StringBuilder();

        if (!autores.isEmpty()) {
            sb.append("Autor: ").append(String.join(", ", autores));
        }

        if (editorial != null && !editorial.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("Editorial: ").append(editorial);
            if (paisEditorial != null && !paisEditorial.isEmpty()) {
                sb.append(" (").append(paisEditorial).append(")");
            }
        }

        return sb.toString();
    }
}
