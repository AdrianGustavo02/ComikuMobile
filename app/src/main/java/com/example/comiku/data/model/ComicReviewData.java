package com.example.comiku.data.model;

import java.util.Date;

// Datos de una resena de usuario sobre un comic.
public final class ComicReviewData {
    public final String id;
    public final String usuarioId;
    public final String descripcion;
    public final int calificacion;
    public final Date fecha;

    public ComicReviewData(String id, String usuarioId, String descripcion, int calificacion, Date fecha) {
        this.id = id;
        this.usuarioId = usuarioId != null ? usuarioId : "";
        this.descripcion = descripcion != null ? descripcion : "";
        this.calificacion = calificacion;
        this.fecha = fecha;
    }
}
