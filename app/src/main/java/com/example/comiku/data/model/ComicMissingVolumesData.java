package com.example.comiku.data.model;

import java.util.ArrayList;
import java.util.List;

public final class ComicMissingVolumesData {
    public final boolean tieneComicEnBiblioteca;
    public final List<MissingVolumeData> tomosFaltantes;

    // Guarda el estado de tomos faltantes para un comic puntual.
    public ComicMissingVolumesData(boolean tieneComicEnBiblioteca, List<MissingVolumeData> tomosFaltantes) {
        this.tieneComicEnBiblioteca = tieneComicEnBiblioteca;
        this.tomosFaltantes = tomosFaltantes != null ? new ArrayList<>(tomosFaltantes) : new ArrayList<>();
    }
}
