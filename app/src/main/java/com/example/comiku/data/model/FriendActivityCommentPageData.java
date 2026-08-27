package com.example.comiku.data.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

// Resultado paginado de comentarios de una actividad.
public final class FriendActivityCommentPageData {
    public final List<FriendActivityCommentData> comentarios;
    public final String cursorId;
    public final Date cursorFecha;
    public final boolean hayMas;

    public FriendActivityCommentPageData(
            List<FriendActivityCommentData> comentarios,
            String cursorId,
            Date cursorFecha,
            boolean hayMas
    ) {
        this.comentarios = comentarios != null ? comentarios : new ArrayList<>();
        this.cursorId = cursorId;
        this.cursorFecha = cursorFecha;
        this.hayMas = hayMas;
    }
}

