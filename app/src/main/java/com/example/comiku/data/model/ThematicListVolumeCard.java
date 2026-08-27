package com.example.comiku.data.model;

public final class ThematicListVolumeCard {
    public final String comicId;
    public final String tomoId;
    public final String comicNombre;
    public final VolumeDetailData tomoData;
    public final int orden;

    // Modelo UI para mostrar un tomo en la lista de seleccionados.
    public ThematicListVolumeCard(
            String comicId,
            String tomoId,
            String comicNombre,
            VolumeDetailData tomoData,
            int orden
    ) {
        this.comicId = comicId != null ? comicId : "";
        this.tomoId = tomoId != null ? tomoId : "";
        this.comicNombre = comicNombre != null ? comicNombre : "";
        this.tomoData = tomoData;
        this.orden = orden;
    }
}
