package com.example.comiku.core.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;

import com.example.comiku.R;

public final class DeleteConfirmDialogComponent {
    public interface OnConfirmListener {
        void onConfirm();
    }

    private final Context contexto;
    private final String titulo;
    private final String mensaje;
    private final String textoCancelar;
    private final String textoConfirmar;
    private final OnConfirmListener listenerConfirmacion;

    // Prepara el dialogo con su titulo, mensaje y accion de confirmacion.
    public DeleteConfirmDialogComponent(
            Context contexto,
            String titulo,
            String mensaje,
            OnConfirmListener listenerConfirmacion
    ) {
        this(contexto, titulo, mensaje, contexto.getString(R.string.reporte_boton_cancelar), contexto.getString(R.string.amigos_solicitud_aceptar), listenerConfirmacion);
    }

    // Prepara el dialogo con textos personalizados para cada boton.
    public DeleteConfirmDialogComponent(
            Context contexto,
            String titulo,
            String mensaje,
            String textoCancelar,
            String textoConfirmar,
            OnConfirmListener listenerConfirmacion
    ) {
        this.contexto = contexto;
        this.titulo = titulo;
        this.mensaje = mensaje;
        this.textoCancelar = textoCancelar;
        this.textoConfirmar = textoConfirmar;
        this.listenerConfirmacion = listenerConfirmacion;
    }

    // Muestra el dialogo de confirmacion ya estilizado.
    public void show() {
        AlertDialog dialogo = new AlertDialog.Builder(contexto)
                .setTitle(titulo)
                .setMessage(mensaje)
                .setNegativeButton(textoCancelar, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(textoConfirmar, (dialog, which) -> {
                    if (listenerConfirmacion != null) {
                        listenerConfirmacion.onConfirm();
                    }
                })
                .create();

        dialogo.setOnShowListener(dialogInterface -> {
            Button botonCancelar = dialogo.getButton(AlertDialog.BUTTON_NEGATIVE);
            Button botonAceptar = dialogo.getButton(AlertDialog.BUTTON_POSITIVE);

            if (botonCancelar != null) {
                applyButtonStyle(
                        botonCancelar,
                        R.drawable.bg_button_danger,
                        contexto.getColorStateList(R.color.button_danger_text)
                );
            }

            if (botonAceptar != null) {
                applyButtonStyle(
                        botonAceptar,
                        R.drawable.bg_button_primary_action,
                        contexto.getColorStateList(R.color.button_primary_action_text)
                );
            }

            applyButtonsSpacing(botonCancelar, botonAceptar);
        });

        dialogo.show();
        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setBackgroundDrawableResource(R.drawable.bg_report_dialog_rounded);
        }
    }

    // Aplica el estilo visual de accion en cada boton.
    private void applyButtonStyle(Button boton, int fondoResId, ColorStateList colorTexto) {
        boton.setAllCaps(false);
        boton.setBackgroundResource(fondoResId);
        boton.setBackgroundTintList(null);
        boton.setTextColor(colorTexto);
        boton.setTypeface(null, Typeface.BOLD);
        boton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        boton.setPadding(dpToPx(16), dpToPx(11), dpToPx(16), dpToPx(11));
        boton.setMinHeight(0);
    }

    // Separa visualmente ambos botones del dialogo.
    private void applyButtonsSpacing(Button botonCancelar, Button botonAceptar) {
        applyButtonMargin(botonCancelar, 0, 8);
        applyButtonMargin(botonAceptar, 8, 0);
    }

    // Agrega margen lateral cuando el contenedor lo soporta.
    private void applyButtonMargin(Button boton, int margenInicioDp, int margenFinDp) {
        if (boton == null) {
            return;
        }

        ViewGroup.LayoutParams params = boton.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }

        ViewGroup.MarginLayoutParams paramsMargen = (ViewGroup.MarginLayoutParams) params;
        paramsMargen.setMarginStart(dpToPx(margenInicioDp));
        paramsMargen.setMarginEnd(dpToPx(margenFinDp));
        boton.setLayoutParams(paramsMargen);
    }

    // Convierte dp a pixeles para mantener medidas consistentes.
    private int dpToPx(int valorDp) {
        float densidad = contexto.getResources().getDisplayMetrics().density;
        return Math.round(valorDp * densidad);
    }
}
