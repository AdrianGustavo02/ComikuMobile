package com.example.comiku.screens;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

import com.example.comiku.R;

// Dibuja una estrella con relleno y borde cuando esta activa.
public class ReviewStarView extends AppCompatTextView {
    private final Paint pinturaBorde = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pinturaRelleno = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean estrellaLlena;

    // Crea la vista desde codigo.
    public ReviewStarView(Context contexto) {
        super(contexto);
        init();
    }

    // Crea la vista desde XML.
    public ReviewStarView(Context contexto, AttributeSet atributos) {
        super(contexto, atributos);
        init();
    }

    public ReviewStarView(Context contexto, AttributeSet atributos, int estiloDefecto) {
        super(contexto, atributos, estiloDefecto);
        init();
    }

    // Prepara colores y grosor de dibujo.
    private void init() {
        pinturaBorde.setStyle(Paint.Style.STROKE);
        pinturaBorde.setStrokeWidth(getResources().getDisplayMetrics().density * 1.4f);
        pinturaBorde.setColor(getContext().getColor(R.color.review_star_stroke));

        pinturaRelleno.setStyle(Paint.Style.FILL);
        pinturaRelleno.setColor(getContext().getColor(R.color.review_star_fill));
    }

    // Cambia si la estrella se ve llena o vacia.
    public void setFilled(boolean llena) {
        estrellaLlena = llena;
        setText(llena ? getContext().getString(R.string.resenas_estrella_llena)
                : getContext().getString(R.string.resenas_estrella_vacia));
        invalidate();
    }

    // Dibuja la estrella con o sin borde segun su estado.
    @Override
    protected void onDraw(Canvas lienzo) {
        if (!estrellaLlena) {
            super.onDraw(lienzo);
            return;
        }

        CharSequence texto = getText();
        if (texto == null || texto.length() == 0) {
            return;
        }

        String simbolo = texto.toString();
        Paint pinturaBase = getPaint();
        float ejeX = (getWidth() - pinturaBase.measureText(simbolo)) / 2f;
        Paint.FontMetrics metricas = pinturaBase.getFontMetrics();
        float ejeY = (getHeight() - metricas.ascent - metricas.descent) / 2f;

        copiarEstiloTexto(pinturaBorde, pinturaBase);
        copiarEstiloTexto(pinturaRelleno, pinturaBase);

        lienzo.drawText(simbolo, ejeX, ejeY, pinturaBorde);
        lienzo.drawText(simbolo, ejeX, ejeY, pinturaRelleno);
    }

    // Copia el estilo de texto actual a la pintura usada al dibujar.
    private void copiarEstiloTexto(Paint destino, Paint origen) {
        destino.setTextSize(origen.getTextSize());
        destino.setTypeface(origen.getTypeface());
        destino.setTextAlign(origen.getTextAlign());
        destino.setLetterSpacing(origen.getLetterSpacing());
    }
}
