package com.example.comiku.data.model;

import java.util.Map;

public final class VolumeDetailData {
    public final String id;
    public final String comicId;
    public final Integer numeroTomo;
    public final Boolean tomoUnico;
    public final Long isbn;
    public final String fechaPublicacion;
    public final Map<String, Object> portada;

    // Crea un objeto de detalle del tomo desde datos de Firestore.
    public VolumeDetailData(
            String id,
            String comicId,
            Integer numeroTomo,
            Boolean tomoUnico,
            Long isbn,
            String fechaPublicacion,
            Map<String, Object> portada
    ) {
        this.id = id;
        this.comicId = comicId;
        this.numeroTomo = numeroTomo;
        this.tomoUnico = tomoUnico != null ? tomoUnico : false;
        this.isbn = isbn;
        this.fechaPublicacion = fechaPublicacion;
        this.portada = portada;
    }

    // Devuelve el numero de tomo formateado.
    public String getNumeroFormateado() {
        if (tomoUnico) {
            return "Tomo unico";
        }
        return "Tomo " + (numeroTomo != null ? numeroTomo : "N/A");
    }

    // Devuelve la URL de datos de la portada si existe.
    public String getPortadaDataUrl() {
        if (portada == null) {
            return null;
        }
        Object dataUrl = portada.get("dataUrl");
        return dataUrl != null ? String.valueOf(dataUrl) : null;
    }
}
