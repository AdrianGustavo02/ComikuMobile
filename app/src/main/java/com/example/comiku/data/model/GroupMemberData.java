package com.example.comiku.data.model;

// Modelo para representar un miembro de grupo en la interfaz
public class GroupMemberData {
    private String uid;
    private String nick;
    private String fotoPerfil;
    private boolean admin;

    public GroupMemberData(String uid, String nick, String fotoPerfil, boolean admin) {
        this.uid = uid;
        this.nick = nick;
        this.fotoPerfil = fotoPerfil;
        this.admin = admin;
    }

    public String getUid() {
        return uid;
    }

    public String getNick() {
        return nick;
    }

    public String getFotoPerfil() {
        return fotoPerfil;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }
}
