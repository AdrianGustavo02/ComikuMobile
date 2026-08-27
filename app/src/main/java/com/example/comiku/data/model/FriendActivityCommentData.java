package com.example.comiku.data.model;

import java.util.Date;

// Datos de un comentario realizado en una actividad.
public final class FriendActivityCommentData {
    public final String id;
    public final String userId;
    public final String nick;
    public final String fotoPerfilDataUrl;
    public final String texto;
    public final Date fecha;

    public FriendActivityCommentData(
            String id,
            String userId,
            String nick,
            String fotoPerfilDataUrl,
            String texto,
            Date fecha
    ) {
        this.id = id != null ? id : "";
        this.userId = userId != null ? userId : "";
        this.nick = nick != null ? nick : "";
        this.fotoPerfilDataUrl = fotoPerfilDataUrl != null ? fotoPerfilDataUrl : "";
        this.texto = texto != null ? texto : "";
        this.fecha = fecha;
    }
}

