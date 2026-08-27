package com.example.comiku.data.model;

import java.util.ArrayList;
import java.util.List;

// Resultado paginado de resenas de un comic.
public final class ComicReviewPageData {
    public final List<ComicReviewData> resenas;
    public final String ultimoId;
    public final boolean hayMas;

    public ComicReviewPageData(List<ComicReviewData> resenas, String ultimoId, boolean hayMas) {
        this.resenas = resenas != null ? resenas : new ArrayList<>();
        this.ultimoId = ultimoId;
        this.hayMas = hayMas;
    }
}
