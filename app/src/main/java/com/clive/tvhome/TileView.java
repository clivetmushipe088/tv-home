package com.clive.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;

/**
 * One rectangular app tile (322 x 172 design px). Draws the design's brand tile, the app's
 * own TV banner, its icon with name, or an input's name. Focus lifts and scales it on the
 * GPU and fades in a white ring; nothing else is redrawn.
 */
final class TileView extends View {

    static final float W = 322, H = 172, RADIUS = 26;
    static final PathInterpolator SPRING = new PathInterpolator(.34f, 1.3f, .5f, 1f);
    static final PathInterpolator EASE = new PathInterpolator(.22f, .61f, .24f, 1f);

    final Catalog.Item item;
    private final float s;
    private final Bitmap brandTile;
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint big = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Rect dst = new Rect();
    private final int bg;
    private float ringAlpha;
    private ValueAnimator ringAnim;
    private boolean on;

    TileView(Context c, float s, Catalog.Item item, Bitmap brandTile, Typeface bold, Typeface semibold) {
        super(c);
        this.s = s;
        this.item = item;
        this.brandTile = brandTile;
        setFocusable(false);
        setContentDescription(item.label);

        if (brandTile != null) bg = 0xFF000000;
        else if ("input".equals(item.kind)) bg = 0xFF222B31;
        else if (item.banner != null) bg = 0xFF0E1215;
        else bg = 0xFF2B343B;

        gloss.setShader(new LinearGradient(0, 0, 0, H * s * .46f, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(5 * s);
        ring.setColor(0xF5FFFFFF);

        big.setColor(0xFFF4F4F1);
        big.setTypeface(bold);
        big.setTextAlign(Paint.Align.CENTER);
        small.setTextAlign(Paint.Align.CENTER);
        small.setTypeface(semibold);

        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), RADIUS * TileView.this.s);
            }
        });
        setClipToOutline(true);
        setElevation(7 * s);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (brandTile != null) {
            dst.set(0, 0, w, h);
            c.drawBitmap(brandTile, null, dst, bmpPaint);
        } else {
            c.drawColor(bg);
            if ("input".equals(item.kind)) {
                float size = (item.label.length() > 7 ? 44 : 58) * s;
                big.setTextSize(size);
                big.setLetterSpacing(-2f / 58f);
                drawCentered(c, item.label, w / 2f, h / 2f, big, w - 24 * s);
                small.setTextSize(22 * s);
                small.setColor(0x99FFFFFF);
                small.setLetterSpacing(.02f);
                c.drawText("INPUT", w / 2f, h - 18 * s - small.descent(), small);
            } else if (item.banner != null) {
                // cover: fill the width, centre vertically
                int bw = item.banner.getIntrinsicWidth(), bh = item.banner.getIntrinsicHeight();
                float ratio = bw > 0 && bh > 0 ? (float) bh / bw : 9f / 16f;
                int dh = Math.round(w * ratio);
                if (dh < h) {
                    int dw = Math.round(h / ratio);
                    item.banner.setBounds((w - dw) / 2, 0, (w + dw) / 2, h);
                } else {
                    item.banner.setBounds(0, (h - dh) / 2, w, (h + dh) / 2);
                }
                item.banner.draw(c);
            } else if (item.icon != null) {
                int is = Math.round(92 * s), top = Math.round(19 * s);
                item.icon.setBounds((w - is) / 2, top, (w + is) / 2, top + is);
                item.icon.draw(c);
                small.setTextSize(24 * s);
                small.setColor(0xE6FFFFFF);
                small.setLetterSpacing(0);
                CharSequence name = TextUtils.ellipsize(item.label, small, 280 * s, TextUtils.TruncateAt.END);
                c.drawText(name, 0, name.length(), w / 2f, top + is + 12 * s - small.ascent(), small);
            } else {
                big.setTextSize(34 * s);
                big.setLetterSpacing(-0.8f / 34f);
                drawCentered(c, item.label, w / 2f, h / 2f, big, 270 * s);
            }
            c.drawRect(0, 0, w, h * .46f, gloss);
        }
        if (ringAlpha > 0) {
            ring.setAlpha(Math.round(245 * ringAlpha));
            float inset = 2.5f * s;
            r.set(inset, inset, w - inset, h - inset);
            c.drawRoundRect(r, RADIUS * s - inset, RADIUS * s - inset, ring);
        }
    }

    private static void drawCentered(Canvas c, String text, float cx, float cy, TextPaint p, float maxW) {
        CharSequence t = TextUtils.ellipsize(text, p, maxW, TextUtils.TruncateAt.END);
        float base = cy - (p.descent() + p.ascent()) / 2f;
        c.drawText(t, 0, t.length(), cx, base, p);
    }

    /** Focus: lift and grow on the GPU, fade the ring in. */
    void setOn(boolean focus, boolean animate) {
        if (focus == on && animate) return;
        on = focus;
        long d = animate ? 280 : 0;
        animate().scaleX(focus ? 1.08f : 1f).scaleY(focus ? 1.08f : 1f)
                .translationY(focus ? -5 * s : 0).translationZ(focus ? 7 * s : 0)
                .setInterpolator(SPRING).setDuration(d).start();
        if (ringAnim != null) ringAnim.cancel();
        if (!animate) {
            ringAlpha = focus ? 1 : 0;
            invalidate();
            return;
        }
        ringAnim = ValueAnimator.ofFloat(ringAlpha, focus ? 1 : 0);
        ringAnim.setDuration(200);
        ringAnim.setInterpolator(EASE);
        ringAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                ringAlpha = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        ringAnim.start();
    }
}
