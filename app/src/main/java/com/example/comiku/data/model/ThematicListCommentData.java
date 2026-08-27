package com.example.comiku.data.model;

import java.util.Date;

// Datos de un comentario en una lista tematica.
public final class ThematicListCommentData {
    public final String id;
    public final String userId;
    public final String comentario;
    public final Date fechaComentario;

    public ThematicListCommentData(String id, String userId, String comentario, Date fechaComentario) {
        this.id = id;
        this.userId = userId != null ? userId : "";
        this.comentario = comentario != null ? comentario : "";
        this.fechaComentario = fechaComentario;
    }
}
