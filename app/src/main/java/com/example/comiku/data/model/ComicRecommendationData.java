package com.example.comiku.data.model;

import java.util.ArrayList;
import java.util.List;

// Datos de un comic recomendado con su tomo destacado y generos coincidentes.
public final class ComicRecommendationData {
    public final String comicId;
    public final String comicNombre;
    public final List<String> comicAutores;
    public final List<String> generosCoincidentes;
    public final VolumeDetailData tomoDestacado;

    public ComicRecommendationData(
            String comicId,
            String comicNombre,
            List<String> comicAutores,
            List<String> generosCoincidentes,
            VolumeDetailData tomoDestacado
    ) {
        this.comicId = comicId;
        this.comicNombre = comicNombre;
        this.comicAutores = comicAutores != null ? comicAutores : new ArrayList<>();
        this.generosCoincidentes = generosCoincidentes != null ? generosCoincidentes : new ArrayList<>();
        this.tomoDestacado = tomoDestacado;
    }
}
