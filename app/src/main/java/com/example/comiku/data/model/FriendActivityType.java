package com.example.comiku.data.model;

// Tipos de actividad
public enum FriendActivityType {
    LIBRARY_ADD("library_add"),
    WISHLIST_ADD("wishlist_add"),
    THEMATIC_LIST_CREATE("thematic_list_create"),
    UNKNOWN("unknown");

    public final String valorFirestore;

    FriendActivityType(String valorFirestore) {
        this.valorFirestore = valorFirestore;
    }

    // Convierte el valor guardado en Firestore al enum interno.
    public static FriendActivityType fromFirestoreValue(String valor) {
        if (LIBRARY_ADD.valorFirestore.equals(valor)) {
            return LIBRARY_ADD;
        }
        if (WISHLIST_ADD.valorFirestore.equals(valor)) {
            return WISHLIST_ADD;
        }
        if (THEMATIC_LIST_CREATE.valorFirestore.equals(valor)) {
            return THEMATIC_LIST_CREATE;
        }
        return UNKNOWN;
    }
}

