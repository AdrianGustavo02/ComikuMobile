package com.example.comiku.data.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

// Paginado de actividades.
public final class FriendActivityPageData {
    public final List<FriendActivityData> actividades;
    public final String cursorId;
    public final Date cursorFecha;
    public final boolean hayMas;

    public FriendActivityPageData(
            List<FriendActivityData> actividades,
            String cursorId,
            Date cursorFecha,
            boolean hayMas
    ) {
        this.actividades = actividades != null ? actividades : new ArrayList<>();
        this.cursorId = cursorId;
        this.cursorFecha = cursorFecha;
        this.hayMas = hayMas;
    }
}

