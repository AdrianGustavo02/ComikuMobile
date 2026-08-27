package com.example.comiku.data.model;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

// Datos de una notificacion visible para el usuario.
public final class AppNotificationData {
    public final String id;
    public final String receptorId;
    public final String actorId;
    public final String actorNick;
    public final String actorFotoPerfilDataUrl;
    public final NotificationType tipoNotificacion;
    public final boolean leida;
    public final Date fechaCreacion;
    public final Map<String, Object> metadata;

    public AppNotificationData(
            String id,
            String receptorId,
            String actorId,
            String actorNick,
            String actorFotoPerfilDataUrl,
            NotificationType tipoNotificacion,
            boolean leida,
            Date fechaCreacion,
            Map<String, Object> metadata
    ) {
        this.id = id != null ? id : "";
        this.receptorId = receptorId != null ? receptorId : "";
        this.actorId = actorId != null ? actorId : "";
        this.actorNick = actorNick != null ? actorNick : "";
        this.actorFotoPerfilDataUrl = actorFotoPerfilDataUrl != null ? actorFotoPerfilDataUrl : "";
        this.tipoNotificacion = tipoNotificacion != null ? tipoNotificacion : NotificationType.UNKNOWN;
        this.leida = leida;
        this.fechaCreacion = fechaCreacion;
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }
}

