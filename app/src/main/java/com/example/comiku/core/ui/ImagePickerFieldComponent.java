package com.example.comiku.core.ui;

import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.example.comiku.R;

import java.util.Locale;

public final class ImagePickerFieldComponent {
    private final View vistaRaiz;
    private final Button botonSeleccionar;
    private final TextView textoNombreArchivo;

    // Inicializa el componente de selector de imagen.
    public ImagePickerFieldComponent(@NonNull View vistaRaiz) {
        this.vistaRaiz = vistaRaiz;
        this.botonSeleccionar = vistaRaiz.findViewById(R.id.botonSelectorImagenComponente);
        this.textoNombreArchivo = vistaRaiz.findViewById(R.id.textoNombreArchivoComponente);
    }

    // Conecta el click del boton para abrir la galeria.
    public void setOnSelectClickListener(View.OnClickListener listener) {
        botonSeleccionar.setOnClickListener(listener);
    }

    // Actualiza el texto del boton segun el contexto.
    public void setButtonText(@StringRes int textoResId) {
        botonSeleccionar.setText(textoResId);
    }

    // Muestra nombre y formato de la imagen elegida.
    public void showSelectedFile(String nombreArchivo, String tipoContenido) {
        String nombreVisible = TextUtils.isEmpty(nombreArchivo)
                ? vistaRaiz.getContext().getString(R.string.image_picker_file_default_name)
                : nombreArchivo;
        String formatoVisible = resolveFormat(nombreVisible, tipoContenido);
        textoNombreArchivo.setText(
                vistaRaiz.getContext().getString(
                        R.string.image_picker_selected_file_format,
                        nombreVisible,
                        formatoVisible
                )
        );
    }

    // Vuelve al texto inicial cuando no hay imagen seleccionada.
    public void clearSelection() {
        textoNombreArchivo.setText(R.string.image_picker_no_file_selected);
    }

    // Habilita o deshabilita el selector completo.
    public void setEnabled(boolean habilitado) {
        botonSeleccionar.setEnabled(habilitado);
        textoNombreArchivo.setEnabled(habilitado);
    }

    // Resuelve el formato visible desde nombre o tipo de contenido.
    private String resolveFormat(String nombreArchivo, String tipoContenido) {
        String extension = extractExtension(nombreArchivo);
        if (!TextUtils.isEmpty(extension)) {
            return extension.toUpperCase(Locale.ROOT);
        }
        if (!TextUtils.isEmpty(tipoContenido) && tipoContenido.contains("/")) {
            String[] partes = tipoContenido.split("/");
            if (partes.length == 2 && !TextUtils.isEmpty(partes[1])) {
                return partes[1].toUpperCase(Locale.ROOT);
            }
        }
        return vistaRaiz.getContext().getString(R.string.image_picker_file_default_format);
    }

    // Obtiene la extension del archivo si existe.
    private String extractExtension(String nombreArchivo) {
        if (TextUtils.isEmpty(nombreArchivo)) {
            return "";
        }
        int indicePunto = nombreArchivo.lastIndexOf('.');
        if (indicePunto <= 0 || indicePunto >= nombreArchivo.length() - 1) {
            return "";
        }
        return nombreArchivo.substring(indicePunto + 1).trim();
    }
}
