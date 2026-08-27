package com.example.comiku.data.model;

import java.util.Date;

public final class RecentLibraryVolumeData {
    public final String comicId;
    public final String comicNombre;
    public final VolumeDetailData tomo;
    public final Date fechaAgregado;

    // Guarda un tomo reciente de biblioteca para inicio.
    public RecentLibraryVolumeData(String comicId, String comicNombre, VolumeDetailData tomo, Date fechaAgregado) {
        this.comicId = comicId;
        this.comicNombre = comicNombre;
        this.tomo = tomo;
        this.fechaAgregado = fechaAgregado;
    }
}
