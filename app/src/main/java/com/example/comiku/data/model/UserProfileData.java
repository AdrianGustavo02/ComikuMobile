package com.example.comiku.data.model;

// Modelo de perfil de usuario para mostrar en chats
public class UserProfileData {
    private String uid;
    private String nick;
    private String nombre;
    private String fotoPerfil;
    private boolean activo;

    public UserProfileData() {
    }

    public UserProfileData(String uid, String nick, String nombre, String fotoPerfil) {
        this.uid = uid;
        this.nick = nick;
        this.nombre = nombre;
        this.fotoPerfil = fotoPerfil;
        this.activo = true;
    }

    // Getters y Setters
    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getFotoPerfil() {
        return fotoPerfil;
    }

    public void setFotoPerfil(String fotoPerfil) {
        this.fotoPerfil = fotoPerfil;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
