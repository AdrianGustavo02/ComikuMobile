package com.example.comiku.data.model;

import java.util.Date;

public class FriendRequestData {
    public final String senderUid;
    public final String nick;
    public final String fotoPerfilDataUrl;
    public final Date fechaSolicitud;

    // Crea un modelo para solicitudes pendientes.
    public FriendRequestData(String senderUid, String nick, String fotoPerfilDataUrl, Date fechaSolicitud) {
        this.senderUid = senderUid;
        this.nick = nick;
        this.fotoPerfilDataUrl = fotoPerfilDataUrl;
        this.fechaSolicitud = fechaSolicitud;
    }
}
