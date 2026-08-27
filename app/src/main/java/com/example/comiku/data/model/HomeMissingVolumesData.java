package com.example.comiku.data.model;

import java.util.ArrayList;
import java.util.List;

public final class HomeMissingVolumesData {
    public final boolean tieneTomosEnBiblioteca;
    public final List<MissingVolumeData> tomosFaltantes;

    // Guarda los tomos faltantes para la pantalla de inicio.
    public HomeMissingVolumesData(boolean tieneTomosEnBiblioteca, List<MissingVolumeData> tomosFaltantes) {
        this.tieneTomosEnBiblioteca = tieneTomosEnBiblioteca;
        this.tomosFaltantes = tomosFaltantes != null ? new ArrayList<>(tomosFaltantes) : new ArrayList<>();
    }
}
