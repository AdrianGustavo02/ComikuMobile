package com.example.comiku.data.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class LibraryVolumeReadingData {
    public final boolean enBiblioteca;
    public final boolean leido;
    public final List<Date> fechasLectura;
    public final List<LibraryReadingEntryData> readingEntries;

    // Guarda el estado de lectura de un tomo en biblioteca.
    public LibraryVolumeReadingData(
            boolean enBiblioteca,
            boolean leido,
            List<Date> fechasLectura,
            List<LibraryReadingEntryData> readingEntries
    ) {
        this.enBiblioteca = enBiblioteca;
        this.leido = leido;
        this.fechasLectura = fechasLectura != null ? new ArrayList<>(fechasLectura) : new ArrayList<>();
        this.readingEntries = readingEntries != null ? new ArrayList<>(readingEntries) : new ArrayList<>();
    }
}
