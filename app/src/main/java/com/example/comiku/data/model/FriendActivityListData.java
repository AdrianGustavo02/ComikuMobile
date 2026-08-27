package com.example.comiku.data.model;

// Datos minimos de una lista tematica mostrada en una actividad.
public final class FriendActivityListData {
    public final String listaId;
    public final String nombreLista;

    public FriendActivityListData(String listaId, String nombreLista) {
        this.listaId = listaId != null ? listaId : "";
        this.nombreLista = nombreLista != null ? nombreLista : "";
    }
}

