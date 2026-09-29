package com.example.comiku.screens;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageView;

public class RoundedImageView extends AppCompatImageView {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();
    private float cornerRadius = 12f;
    private boolean circular;

    public RoundedImageView(Context context) {
        super(context);
    }

    public RoundedImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RoundedImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setCornerRadius(float radius) {
        this.cornerRadius = radius;
        invalidate();
    }

    // Cambia la vista para que se dibuje como circulo.
    public void setCircular(boolean circular) {
        this.circular = circular;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getDrawable() == null) {
            super.onDraw(canvas);
            return;
        }

        if (!(getDrawable() instanceof BitmapDrawable)) {
            super.onDraw(canvas);
            return;
        }

        Bitmap bitmap = ((BitmapDrawable) getDrawable()).getBitmap();
        if (bitmap == null) {
            super.onDraw(canvas);
            return;
        }

        float viewWidth = getWidth();
        float viewHeight = getHeight();
        float bitmapWidth = bitmap.getWidth();
        float bitmapHeight = bitmap.getHeight();

        float scale = Math.max(viewWidth / bitmapWidth, viewHeight / bitmapHeight);
        float scaledWidth = bitmapWidth * scale;
        float scaledHeight = bitmapHeight * scale;
        float left = (viewWidth - scaledWidth) / 2f;
        float top = (viewHeight - scaledHeight) / 2f;
        rectF.set(0, 0, viewWidth, viewHeight);

        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate(left, top);

        BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        shader.setLocalMatrix(matrix);
        paint.setShader(shader);

        if (circular) {
            float radio = Math.min(viewWidth, viewHeight) / 2f;
            canvas.drawCircle(viewWidth / 2f, viewHeight / 2f, radio, paint);
            return;
        }

        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);
    }
}
