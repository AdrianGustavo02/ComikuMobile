package com.example.comiku.core.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;

import java.util.LinkedHashMap;
import java.util.Map;

import com.example.comiku.R;

import io.getstream.chat.android.ui.helper.SupportedReactions;

// Crea las reacciones de emoji que se muestran al mantener apretado un mensaje.
public final class ReactionEmojiProvider {

    private ReactionEmojiProvider() {
    }


    public static SupportedReactions createSupportedReactions(Context contexto) {
        Map<String, SupportedReactions.ReactionDrawable> reacciones = new LinkedHashMap<>();
        reacciones.put(SupportedReactions.DefaultReactionTypes.LOL, crearEmoji(contexto, "😂"));
        reacciones.put(SupportedReactions.DefaultReactionTypes.THUMBS_UP, crearEmoji(contexto, "👍"));
        reacciones.put(SupportedReactions.DefaultReactionTypes.LOVE, crearEmoji(contexto, "❤️"));
        reacciones.put(SupportedReactions.DefaultReactionTypes.THUMBS_DOWN, crearEmoji(contexto, "😞"));
        reacciones.put(SupportedReactions.DefaultReactionTypes.WUT, crearEmoji(contexto, "😲"));
        return new SupportedReactions(contexto, reacciones);
    }

    // Crea un icono simple con un emoji centrado para usarlo en reacciones.
    private static SupportedReactions.ReactionDrawable crearEmoji(Context contexto, String emoji) {
        int colorInactivo = contexto.getResources().getColor(R.color.gray_light);
        int colorActivo = contexto.getResources().getColor(R.color.blue);
        return new SupportedReactions.ReactionDrawable(
                new EmojiReactionDrawable(contexto, emoji, colorInactivo),
                new EmojiReactionDrawable(contexto, emoji, colorActivo)
        );
    }

    // Dibuja un emoji dentro de un circulo para que quede visible en el selector de reacciones.
    private static final class EmojiReactionDrawable extends Drawable {
        private final Paint fondo;
        private final Paint texto;
        private final String emoji;

        EmojiReactionDrawable(Context contexto, String emoji, int colorFondo) {
            this.emoji = emoji;
            float densidad = contexto.getResources().getDisplayMetrics().density;
            fondo = new Paint(Paint.ANTI_ALIAS_FLAG);
            fondo.setColor(colorFondo);
            texto = new Paint(Paint.ANTI_ALIAS_FLAG);
            texto.setColor(Color.WHITE);
            texto.setTypeface(Typeface.DEFAULT_BOLD);
            texto.setTextAlign(Paint.Align.CENTER);
            texto.setTextSize(18f * densidad);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect area = getBounds();
            float centroX = area.exactCenterX();
            float centroY = area.exactCenterY();
            float radio = Math.min(area.width(), area.height()) / 2f;
            canvas.drawCircle(centroX, centroY, radio, fondo);

            Paint.FontMetrics fontMetrics = texto.getFontMetrics();
            float baseline = centroY - (fontMetrics.ascent + fontMetrics.descent) / 2f;
            canvas.drawText(emoji, centroX, baseline, texto);
        }

        @Override
        public void setAlpha(int alpha) {
            fondo.setAlpha(alpha);
            texto.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            fondo.setColorFilter(colorFilter);
            texto.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }
}
