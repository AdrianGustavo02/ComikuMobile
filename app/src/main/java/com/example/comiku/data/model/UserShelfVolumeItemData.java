package com.example.comiku.data.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class UserShelfVolumeItemData {
    public final VolumeDetailData tomo;
    public final Date fechaAgregado;
    public final boolean leido;
    public final List<Date> fechasLectura;

    // Guarda un tomo dentro de una lista del usuario.
    public UserShelfVolumeItemData(
            VolumeDetailData tomo,
            Date fechaAgregado,
            boolean leido,
            List<Date> fechasLectura
    ) {
        this.tomo = tomo;
        this.fechaAgregado = fechaAgregado;
        this.leido = leido;
        this.fechasLectura = fechasLectura != null ? new ArrayList<>(fechasLectura) : new ArrayList<>();
    }
}
