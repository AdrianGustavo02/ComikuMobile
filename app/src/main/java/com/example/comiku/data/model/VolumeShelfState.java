package com.example.comiku.data.model;

public final class VolumeShelfState {
    public final boolean enBiblioteca;
    public final boolean enDeseados;

    // Guarda el estado del tomo en biblioteca y deseados.
    public VolumeShelfState(boolean enBiblioteca, boolean enDeseados) {
        this.enBiblioteca = enBiblioteca;
        this.enDeseados = enDeseados;
    }
}
