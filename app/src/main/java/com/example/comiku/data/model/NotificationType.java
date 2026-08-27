package com.example.comiku.data.model;

// Tipos de notificacion permitidos en la app.
public enum NotificationType {
    ACTIVITY_LIKE("activity_like"),
    ACTIVITY_COMMENT("activity_comment"),
    FRIEND_REQUEST("friend_request"),
    THEMATIC_LIST_LIKE("thematic_list_like"),
    THEMATIC_LIST_COMMENT("thematic_list_comment"),
    UNKNOWN("unknown");

    public final String firestoreValue;

    NotificationType(String firestoreValue) {
        this.firestoreValue = firestoreValue;
    }

    // Convierte el valor guardado en Firestore al enum interno.
    public static NotificationType fromFirestoreValue(String value) {
        if (ACTIVITY_LIKE.firestoreValue.equals(value)) {
            return ACTIVITY_LIKE;
        }
        if (ACTIVITY_COMMENT.firestoreValue.equals(value)) {
            return ACTIVITY_COMMENT;
        }
        if (FRIEND_REQUEST.firestoreValue.equals(value)) {
            return FRIEND_REQUEST;
        }
        if (THEMATIC_LIST_LIKE.firestoreValue.equals(value)) {
            return THEMATIC_LIST_LIKE;
        }
        if (THEMATIC_LIST_COMMENT.firestoreValue.equals(value)) {
            return THEMATIC_LIST_COMMENT;
        }
        return UNKNOWN;
    }
}

