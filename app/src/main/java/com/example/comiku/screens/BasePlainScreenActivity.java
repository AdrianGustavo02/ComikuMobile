package com.example.comiku.screens;

import android.graphics.Color;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.ColorRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.comiku.R;

public abstract class BasePlainScreenActivity extends AppCompatActivity {
    // Prepara una pantalla simple sin navbar con color oscuro arriba.
    protected void setupPlainScreenShell(@LayoutRes int layoutResId) {
        setContentView(R.layout.activity_plain_shell);
        applyShellBackgroundColor();
        applyStatusBarColor();
        applyTopCapVisibility();
        inflateContent(layoutResId);
    }

    // Devuelve el color de fondo del shell para pantallas sin navbar.
    @ColorRes
    protected int getShellBackgroundColorRes() {
        return android.R.color.white;
    }

    // Permite ocultar el remate superior cuando una pantalla necesita continuidad visual con la barra de estado.
    protected boolean shouldShowTopCap() {
        return true;
    }

    // Aplica el color de fondo del shell para evitar cortes visuales.
    private void applyShellBackgroundColor() {
        View layoutPantallaPlana = findViewById(R.id.layoutPantallaPlana);
        if (layoutPantallaPlana == null) {
            return;
        }
        layoutPantallaPlana.setBackgroundColor(
                ContextCompat.getColor(this, getShellBackgroundColorRes())
        );
    }

    // Pone el mismo color oscuro en la barra de estado.
    private void applyStatusBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#12091D"));
        }
    }

    // Muestra u oculta el remate superior segun la necesidad de cada pantalla.
    private void applyTopCapVisibility() {
        View remateSuperior = findViewById(R.id.viewRemateSuperiorPlano);
        if (remateSuperior == null) {
            return;
        }
        remateSuperior.setVisibility(shouldShowTopCap() ? View.VISIBLE : View.GONE);
    }

    // Mete el layout real dentro del contenedor comun de la pantalla.
    private void inflateContent(@LayoutRes int layoutResId) {
        FrameLayout contenedorContenido = findViewById(R.id.contenedorContenidoPlano);
        if (contenedorContenido == null) {
            return;
        }
        LayoutInflater.from(this).inflate(layoutResId, contenedorContenido, true);
    }
}
