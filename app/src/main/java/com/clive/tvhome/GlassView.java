package com.clive.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewOutlineProvider;

/**
 * Frosted glass made from a pre-blurred copy of the wallpaper: it draws the part of the
 * blurred image that sits behind it, then a dark tint. Looks like a live blur, costs a bitmap.
 */
final class GlassView extends View {

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect(), dst = new Rect();
    private final float s;
    private final float x, y, w, h;   // design px, where this view sits on the 1920x1080 stage
    private Bitmap glass;
    private int tint;

    GlassView(Context c, float s, float x, float y, float w, float h, final float radius) {
        super(c);
        this.s = s;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        edge.setColor(0x3DFFFFFF);
        edge.setStrokeWidth(Math.max(1, s));
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius * GlassView.this.s);
            }
        });
        setClipToOutline(true);
    }

    void set(Bitmap glass, int tint) {
        this.glass = glass;
        this.tint = tint;
        if (glass != null) {
            float f = glass.getWidth() / 1920f;
            src.set(Math.round(x * f), Math.round(y * f), Math.round((x + w) * f), Math.round((y + h) * f));
        }
        invalidate();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onDraw(Canvas c) {
        dst.set(0, 0, getWidth(), getHeight());
        if (glass != null) c.drawBitmap(glass, src, dst, paint);
        c.drawColor(tint);
        c.drawLine(0, edge.getStrokeWidth() / 2, getWidth(), edge.getStrokeWidth() / 2, edge);
    }
}
