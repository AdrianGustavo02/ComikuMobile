package com.example.comiku.core.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

public final class ToastUtils {
    private ToastUtils() {
    }

    public static void showTextToast(Context context, int mensajeResId) {
        showTextToast(context, mensajeResId, Toast.LENGTH_SHORT);
    }

    public static void showTextToast(Context context, int mensajeResId, int duracion) {
        if (context == null) {
            return;
        }
        showTextToast(context, context.getString(mensajeResId), duracion);
    }

    public static void showTextToast(Context context, CharSequence mensaje) {
        showTextToast(context, mensaje, Toast.LENGTH_SHORT);
    }

    public static void showTextToast(Context context, CharSequence mensaje, int duracion) {
        if (context == null || TextUtils.isEmpty(mensaje)) {
            return;
        }

        Context contextApp = context.getApplicationContext();
        Toast toast = new Toast(contextApp);
        TextView texto = new TextView(contextApp);
        texto.setText(mensaje);
        texto.setTextColor(Color.WHITE);
        texto.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        texto.setGravity(Gravity.CENTER);
        texto.setPadding(dpToPx(contextApp, 16), dpToPx(contextApp, 8), dpToPx(contextApp, 16), dpToPx(contextApp, 8));
        texto.setMaxLines(3);

        GradientDrawable fondo = new GradientDrawable();
        fondo.setColor(Color.argb(255, 24, 24, 28));
        fondo.setCornerRadius(dpToPx(contextApp, 18));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            texto.setBackground(fondo);
        } else {
            texto.setBackgroundDrawable(fondo);
        }

        toast.setView(texto);
        toast.setDuration(duracion);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dpToPx(contextApp, 80));
        toast.show();
    }

    private static int dpToPx(Context context, int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        ));
    }
}
