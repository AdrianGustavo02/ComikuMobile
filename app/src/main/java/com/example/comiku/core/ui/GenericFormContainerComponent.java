package com.example.comiku.core.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;

import com.example.comiku.R;

// Inserta contenido de formulario dentro de un contenedor visual reutilizable.
public final class GenericFormContainerComponent {

    private GenericFormContainerComponent() {
    }

    // Infla un layout de formulario dentro del contenedor comun de formularios.
    public static void inflateFormContent(@NonNull ViewGroup raiz, @LayoutRes int layoutFormularioResId) {
        ViewGroup contenedorFormulario = raiz.findViewById(R.id.contenedorFormularioGenerico);
        if (contenedorFormulario == null) {
            return;
        }
        contenedorFormulario.removeAllViews();
        LayoutInflater.from(raiz.getContext()).inflate(layoutFormularioResId, contenedorFormulario, true);
    }
}
