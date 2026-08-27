package com.example.comiku.data.model;

public final class ThematicListVolumeData {
    public final String id;
    public final String comicId;
    public final String tomoId;
    public final int orden;

    // Crea un objeto de tomo dentro de una lista tematica.
    public ThematicListVolumeData(String id, String comicId, String tomoId, int orden) {
        this.id = id;
        this.comicId = comicId != null ? comicId : "";
        this.tomoId = tomoId != null ? tomoId : "";
        this.orden = orden;
    }
}
