package com.example.comiku.data.model;

import java.util.ArrayList;
import java.util.List;

// Resultado de recomendaciones para el inicio, con indicador de biblioteca activa.
public final class HomeRecommendationsData {
    public final boolean tieneItemsEnBiblioteca;
    public final List<ComicRecommendationData> recomendaciones;

    public HomeRecommendationsData(boolean tieneItemsEnBiblioteca, List<ComicRecommendationData> recomendaciones) {
        this.tieneItemsEnBiblioteca = tieneItemsEnBiblioteca;
        this.recomendaciones = recomendaciones != null ? recomendaciones : new ArrayList<>();
    }
}
