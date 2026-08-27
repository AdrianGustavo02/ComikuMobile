package com.example.comiku.data.model;

// Datos minimos de un tomo mostrado en una actividad.
public final class FriendActivityVolumeData {
    public final String comicId;
    public final String tomoId;
    public final String portadaDataUrl;

    public FriendActivityVolumeData(String comicId, String tomoId, String portadaDataUrl) {
        this.comicId = comicId != null ? comicId : "";
        this.tomoId = tomoId != null ? tomoId : "";
        this.portadaDataUrl = portadaDataUrl != null ? portadaDataUrl : "";
    }
}

