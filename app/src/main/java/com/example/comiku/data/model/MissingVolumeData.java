package com.example.comiku.data.model;

public final class MissingVolumeData {
    public final String comicId;
    public final String comicNombre;
    public final VolumeDetailData tomo;

    // Guarda un tomo faltante junto al comic al que pertenece.
    public MissingVolumeData(String comicId, String comicNombre, VolumeDetailData tomo) {
        this.comicId = comicId;
        this.comicNombre = comicNombre;
        this.tomo = tomo;
    }
}
