package com.example.comiku.data.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class ThematicListData {
    public final String id;
    public final String userId;
    public final String nombre;
    public final String descripcion;
    public final long cantidadLikes;
    public final long cantidadComentarios;
    public final Date fechaCreacion;
    public final boolean esGuiaDeLectura;
    public final List<String> fotosDePortadas;

    // Crea un objeto de lista tematica desde datos de Firestore.
    public ThematicListData(
            String id,
            String userId,
            String nombre,
            String descripcion,
            long cantidadLikes,
            long cantidadComentarios,
            Date fechaCreacion,
            boolean esGuiaDeLectura,
            List<String> fotosDePortadas
    ) {
        this.id = id;
        this.userId = userId;
        this.nombre = nombre != null ? nombre : "";
        this.descripcion = descripcion != null ? descripcion : "";
        this.cantidadLikes = cantidadLikes;
        this.cantidadComentarios = cantidadComentarios;
        this.fechaCreacion = fechaCreacion;
        this.esGuiaDeLectura = esGuiaDeLectura;
        this.fotosDePortadas = fotosDePortadas != null ? fotosDePortadas : new ArrayList<>();
    }
}
