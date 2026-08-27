package com.example.comiku.data.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

// Datos completos de una actividad del feed de amigos.
public final class FriendActivityData {
    public final String id;
    public final String actorUid;
    public final String actorNick;
    public final String actorFotoPerfilDataUrl;
    public final FriendActivityType tipoActividad;
    public final int cantidadElementos;
    public final List<FriendActivityVolumeData> tomos;
    public final List<FriendActivityListData> listas;
    public final Date fecha;
    public final int cantidadLikes;
    public final int cantidadComentarios;

    public FriendActivityData(
            String id,
            String actorUid,
            String actorNick,
            String actorFotoPerfilDataUrl,
            FriendActivityType tipoActividad,
            int cantidadElementos,
            List<FriendActivityVolumeData> tomos,
            List<FriendActivityListData> listas,
            Date fecha,
            int cantidadLikes,
            int cantidadComentarios
    ) {
        this.id = id != null ? id : "";
        this.actorUid = actorUid != null ? actorUid : "";
        this.actorNick = actorNick != null ? actorNick : "";
        this.actorFotoPerfilDataUrl = actorFotoPerfilDataUrl != null ? actorFotoPerfilDataUrl : "";
        this.tipoActividad = tipoActividad != null ? tipoActividad : FriendActivityType.UNKNOWN;
        this.cantidadElementos = Math.max(0, cantidadElementos);
        this.tomos = tomos != null ? tomos : new ArrayList<>();
        this.listas = listas != null ? listas : new ArrayList<>();
        this.fecha = fecha;
        this.cantidadLikes = Math.max(0, cantidadLikes);
        this.cantidadComentarios = Math.max(0, cantidadComentarios);
    }
}

