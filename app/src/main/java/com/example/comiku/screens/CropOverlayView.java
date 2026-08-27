package com.example.comiku.screens;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public final class CropOverlayView extends View {
    private final Paint paintOscuro = new Paint();
    private final Paint paintMarco = new Paint();
    private RectF cropRect;
    private int aspectX = 1;
    private int aspectY = 1;

    // Crea el marco visual del recorte.
    public CropOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paintOscuro.setColor(0x99000000);
        paintOscuro.setStyle(Paint.Style.FILL);
        paintMarco.setColor(Color.WHITE);
        paintMarco.setStyle(Paint.Style.STROKE);
        paintMarco.setStrokeWidth(4f);
    }

    // Cambia la forma del area de recorte.
    public void setAspectRatio(int x, int y) {
        aspectX = Math.max(1, x);
        aspectY = Math.max(1, y);
        requestLayout();
        invalidate();
    }

    // Devuelve el area visible para recortar.
    public RectF getCropRect() {
        return cropRect == null ? null : new RectF(cropRect);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float margin = dpToPx(24);
        float availableWidth = w - (margin * 2f);
        float availableHeight = h - (margin * 2f);
        float targetRatio = (float) aspectX / (float) aspectY;

        float cropWidth = availableWidth;
        float cropHeight = cropWidth / targetRatio;
        if (cropHeight > availableHeight) {
            cropHeight = availableHeight;
            cropWidth = cropHeight * targetRatio;
        }

        float left = (w - cropWidth) / 2f;
        float top = (h - cropHeight) / 2f;
        cropRect = new RectF(left, top, left + cropWidth, top + cropHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cropRect == null) {
            return;
        }

        canvas.drawRect(0, 0, getWidth(), cropRect.top, paintOscuro);
        canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, paintOscuro);
        canvas.drawRect(cropRect.right, cropRect.top, getWidth(), cropRect.bottom, paintOscuro);
        canvas.drawRect(0, cropRect.bottom, getWidth(), getHeight(), paintOscuro);
        canvas.drawRect(cropRect, paintMarco);
    }

    // Convierte dp a pixeles.
    private int dpToPx(int dp) {
        float densidad = getResources().getDisplayMetrics().density;
        return Math.round(dp * densidad);
    }
}
