package com.clive.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * The clock text with the design's soft white gradient. The big Home clock and the small
 * glass pill on All apps are two instances; text is laid out like CSS line-height: 1 so
 * the big one can scale exactly onto the small one.
 */
final class ClockView extends View {

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final boolean isPill;
    private final float size, padX, padY, radius;
    private String value = "";
    private float textW;

    /** @param isPill small 48px text on a glass pill; otherwise the big 250px clock */
    ClockView(Context c, float s, Typeface tf, boolean isPill) {
        super(c);
        this.isPill = isPill;
        size = (isPill ? 48 : 250) * s;
        padX = isPill ? 30 * s : 40 * s;     // big clock: room for its shadow
        padY = isPill ? 12 * s : 40 * s;
        radius = 36 * s;
        text.setTypeface(tf);
        text.setTextSize(size);
        text.setLetterSpacing(isPill ? -1f / 48f : -6f / 250f);
        int[] colors = isPill ? new int[]{0xFAFFFFFF, 0xDBFFFFFF} : new int[]{0xF5FFFFFF, 0xCCFFFFFF, 0xE0FFFFFF};
        float[] stops = isPill ? null : new float[]{0f, .42f, 1f};
        text.setShader(new LinearGradient(0, padY, 0, padY + size, colors, stops, Shader.TileMode.CLAMP));
        if (!isPill) text.setShadowLayer(14 * s, 0, 14 * s, 0x42000000);
        pill.setColor(0x57080E12);
        edge.setColor(0x4DFFFFFF);
        edge.setStrokeWidth(Math.max(1, s));
    }

    float textLeftInset() { return padX; }
    float textTopInset() { return padY; }
    float textWidth() { return textW; }

    void setText(String v) {
        if (v.equals(value)) return;
        value = v;
        textW = text.measureText(v);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(Math.round(textW + 2 * padX), Math.round(size + 2 * padY));
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (isPill) {
            rect.set(0, 0, getWidth(), getHeight());
            c.drawRoundRect(rect, radius, radius, pill);
            c.drawLine(radius, edge.getStrokeWidth() / 2, getWidth() - radius, edge.getStrokeWidth() / 2, edge);
        }
        Paint.FontMetrics fm = text.getFontMetrics();
        float baseline = padY + (size - (fm.descent - fm.ascent)) / 2f - fm.ascent;
        c.drawText(value, padX, baseline, text);
    }
}
