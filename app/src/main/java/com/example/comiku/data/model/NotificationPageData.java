package com.example.comiku.data.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

// Paginado de notificaciones.
public final class NotificationPageData {
    public final List<AppNotificationData> notificaciones;
    public final String cursorId;
    public final Date cursorFecha;
    public final boolean hayMas;

    public NotificationPageData(
            List<AppNotificationData> notificaciones,
            String cursorId,
            Date cursorFecha,
            boolean hayMas
    ) {
        this.notificaciones = notificaciones != null ? notificaciones : new ArrayList<>();
        this.cursorId = cursorId;
        this.cursorFecha = cursorFecha;
        this.hayMas = hayMas;
    }
}

