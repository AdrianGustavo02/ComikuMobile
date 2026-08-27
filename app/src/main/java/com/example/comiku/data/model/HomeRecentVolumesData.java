package com.example.comiku.data.model;

import java.util.ArrayList;
import java.util.List;

public final class HomeRecentVolumesData {
    public final boolean tieneTomosEnBiblioteca;
    public final List<RecentLibraryVolumeData> tomosRecientes;

    // Guarda los ultimos tomos de biblioteca para la pantalla de inicio.
    public HomeRecentVolumesData(boolean tieneTomosEnBiblioteca, List<RecentLibraryVolumeData> tomosRecientes) {
        this.tieneTomosEnBiblioteca = tieneTomosEnBiblioteca;
        this.tomosRecientes = tomosRecientes != null ? new ArrayList<>(tomosRecientes) : new ArrayList<>();
    }
}
