package com.example.comiku.data.model;

import java.util.Date;

public final class LibraryReadingEntryData {
    public final int storageIndex;
    public final String id;
    public final Date fecha;
    public final Object raw;

    // Guarda una lectura de un tomo en biblioteca.
    public LibraryReadingEntryData(int storageIndex, String id, Date fecha, Object raw) {
        this.storageIndex = storageIndex;
        this.id = id;
        this.fecha = fecha;
        this.raw = raw;
    }
}
