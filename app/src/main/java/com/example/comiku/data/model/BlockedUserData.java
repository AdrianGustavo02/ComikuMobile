package com.example.comiku.data.model;

import java.util.Date;

public final class BlockedUserData {
    public final String uid;
    public final String nick;
    public final String fotoPerfilDataUrl;
    public final Date fechaBloqueo;

    // Crea un modelo simple para usuarios bloqueados.
    public BlockedUserData(String uid, String nick, String fotoPerfilDataUrl, Date fechaBloqueo) {
        this.uid = uid;
        this.nick = nick;
        this.fotoPerfilDataUrl = fotoPerfilDataUrl;
        this.fechaBloqueo = fechaBloqueo;
    }
}
