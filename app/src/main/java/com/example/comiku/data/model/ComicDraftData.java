package com.example.comiku.data.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ComicDraftData implements Serializable {
    private final String nombre;
    private final List<String> autores;
    private final String editorial;
    private final String paisEditorial;
    private final String estado;
    private final String formato;
    private final List<String> generos;
    private final String descripcion;

    // Crea un borrador con los datos del comic.
    public ComicDraftData(
            String nombre,
            List<String> autores,
            String editorial,
            String paisEditorial,
            String estado,
            String formato,
            List<String> generos,
            String descripcion
    ) {
        this.nombre = nombre;
        this.autores = new ArrayList<>(autores);
        this.editorial = editorial;
        this.paisEditorial = paisEditorial;
        this.estado = estado;
        this.formato = formato;
        this.generos = new ArrayList<>(generos);
        this.descripcion = descripcion;
    }

    // Devuelve el payload del comic para carga admin.
    public Map<String, Object> toAdminMap() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("Nombre", nombre);
        payload.put("Autor", new ArrayList<>(autores));
        payload.put("Editorial", editorial);
        payload.put("PaisEditorial", paisEditorial);
        payload.put("Estado", estado);
        payload.put("Genero", new ArrayList<>(generos));
        payload.put("Descripcion", descripcion);
        payload.put("Formato", formato);
        payload.put("PromedioCalificacion", null);
        payload.put("CantidadCalificaciones", null);
        return payload;
    }

    // Devuelve el payload del comic para carga pendiente.
    public Map<String, Object> toPendingMap() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("nombre", nombre);
        payload.put("autores", new ArrayList<>(autores));
        payload.put("editorial", editorial);
        payload.put("paisEditorial", paisEditorial);
        payload.put("estado", estado);
        payload.put("generos", new ArrayList<>(generos));
        payload.put("descripcion", descripcion);
        payload.put("formato", formato);
        return payload;
    }
}
