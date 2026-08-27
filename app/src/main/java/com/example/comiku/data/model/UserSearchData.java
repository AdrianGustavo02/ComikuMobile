package com.example.comiku.data.model;

public class UserSearchData {
    public final String uid;
    public final String nick;
    public final String nombre;
    public final String fotoPerfilDataUrl;

    // Crea un modelo simple para mostrar usuarios en listas.
    public UserSearchData(String uid, String nick, String nombre, String fotoPerfilDataUrl) {
        this.uid = uid;
        this.nick = nick;
        this.nombre = nombre;
        this.fotoPerfilDataUrl = fotoPerfilDataUrl;
    }
}

