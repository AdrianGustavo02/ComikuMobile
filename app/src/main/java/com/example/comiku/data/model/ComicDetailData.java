package com.example.comiku.data.model;

import java.util.ArrayList;
import java.util.List;

public final class ComicDetailData {
    public final String id;
    public final String nombre;
    public final List<String> autores;
    public final String editorial;
    public final String paisEditorial;
    public final String estado;
    public final String formato;
    public final List<String> generos;
    public final String descripcion;
    public final Double promedioCalificacion;
    public final Long cantidadCalificaciones;

    // Crea un objeto de detalle del comic desde datos de Firestore.
    public ComicDetailData(
            String id,
            String nombre,
            List<String> autores,
            String editorial,
            String paisEditorial,
            String estado,
            String formato,
            List<String> generos,
            String descripcion,
            Double promedioCalificacion,
            Long cantidadCalificaciones
    ) {
        this.id = id;
        this.nombre = nombre;
        this.autores = autores != null ? autores : new ArrayList<>();
        this.editorial = editorial;
        this.paisEditorial = paisEditorial;
        this.estado = estado;
        this.formato = formato;
        this.generos = generos != null ? generos : new ArrayList<>();
        this.descripcion = descripcion;
        this.promedioCalificacion = promedioCalificacion;
        this.cantidadCalificaciones = cantidadCalificaciones != null ? cantidadCalificaciones : 0L;
    }

    // Formatea autores en una cadena.
    public String getAutoresFormateados() {
        return String.join(", ", autores);
    }

    // Formatea generos en una cadena.
    public String getGenerosFormateados() {
        return String.join(", ", generos);
    }

    // Formatea calificacion con cantidad de votos.
    public String getCalificacionFormateada() {
        if (promedioCalificacion == null || cantidadCalificaciones == 0) {
            return "Sin calificaciones";
        }
        return String.format("%.1f/5 (%d votos)", promedioCalificacion, cantidadCalificaciones);
    }
}
