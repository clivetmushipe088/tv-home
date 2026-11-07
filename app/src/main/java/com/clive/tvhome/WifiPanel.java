package com.clive.tvhome;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The design's Wi-Fi popover (540 px glass panel under the Wi-Fi button), drawn natively. */
final class WifiPanel extends FrameLayout {

    private static final float X = 1258, Y = 176, W = 540;

    private static final class Row {
        String name;
        boolean cur, lock, eth, other;
    }

    private final Activity act;
    private final float s;
    private final Typeface semi, medium;
    private final List<Row> rows = new ArrayList<>();
    private final List<View> rowViews = new ArrayList<>();
    private final List<GradientDrawable> rowBgs = new ArrayList<>();
    private Bitmap glass;
    private int idx;
    private boolean open;

    WifiPanel(Activity act, float s, Typeface semi, Typeface medium) {
        super(act);
        this.act = act;
        this.s = s;
        this.semi = semi;
        this.medium = medium;
        setClipChildren(false);
        setVisibility(GONE);
        setPivotX(W * s);
        setPivotY(0);
        setElevation(30 * s);
    }

    void setGlass(Bitmap g) {
        glass = g;
    }

    boolean isOpen() {
        return open;
    }

    void open() {
        build();
        open = true;
        setVisibility(VISIBLE);
        setAlpha(0f);
        setScaleX(.88f);
        setScaleY(.88f);
        setTranslationY(-12 * s);
        animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0)
                .setDuration(440).setInterpolator(TileView.SPRING).withLayer().withEndAction(null).start();
    }

    void close() {
        if (!open) return;
        open = false;
        animate().alpha(0f).scaleX(.9f).scaleY(.9f).translationY(-10 * s)
                .setDuration(200).setInterpolator(new PathInterpolator(.4f, 0f, 1f, 1f)).withLayer()
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (!open) setVisibility(GONE);
                    }
                }).start();
    }

    /** Rebuild if open (e.g. after the location permission answer or a network change). */
    void refresh() {
        if (open) build();
    }

    /** @return true if the key was used */
    boolean key(String k) {
        if (!open) return false;
        switch (k) {
            case "up": if (idx > 0) idx--; paint(); break;
            case "down": if (idx < rows.size() - 1) idx++; paint(); break;
            case "ok":
                Row r = rows.get(idx);
                close();
                if (!r.cur) {
                    // Joining a network needs its password: hand over to the TV's Wi-Fi settings.
                    try {
                        act.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Exception ignored) {
                    }
                }
                break;
            case "back": case "menu": close(); break;
            default: break;
        }
        return true;
    }

    // ------------------------------------------------------------------ build

    private void build() {
        removeAllViews();
        rowViews.clear();
        rowBgs.clear();
        String status = gather();
        idx = Math.min(idx, rows.size() - 1);
        if (!open) idx = 0;

        float h = 20 + 60 + rows.size() * 72 + 20;
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null) {
            lp.height = Math.round(h * s);
            setLayoutParams(lp);
        }

        GlassView g = new GlassView(act, s, X, Y, W, h, 44);
        g.set(glass, 0x80080E12);
        add(g, 0, 0, W, h);

        TextView title = text("Wi-Fi", semi, 30, 0xFFF4F4F1);
        add(title, 36, 20, 300, 60);
        TextView st = text(status, medium, 24, 0xCCFFFFFF);
        st.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        add(st, 236, 20, 268, 60);

        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            FrameLayout row = new FrameLayout(act);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(24 * s);
            row.setBackground(bg);
            add(row, 20, 80 + i * 72, 500, 72);
            ImageView ic = icon(r.eth ? R.drawable.ic_ethernet : R.drawable.ic_wifi);
            addTo(row, ic, 16, 20, 32, 32);
            TextView name = text(r.name, medium, 30, 0xFFF4F4F1);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            addTo(row, name, 64, 0, 332, 72);
            ImageView lock = icon(R.drawable.ic_lock);
            lock.setAlpha(r.lock ? .7f : 0f);
            addTo(row, lock, 412, 23, 26, 26);
            ImageView tick = icon(R.drawable.ic_tick);
            tick.setAlpha(r.cur ? 1f : 0f);
            addTo(row, tick, 454, 21, 30, 30);
            rowViews.add(row);
            rowBgs.add(bg);
        }
        paint();
    }

    private void paint() {
        for (int i = 0; i < rowBgs.size(); i++) {
            GradientDrawable bg = rowBgs.get(i);
            boolean focus = i == idx, cur = rows.get(i).cur;
            bg.setColor(focus ? 0x33FFFFFF : cur ? 0x24FFFFFF : 0x00FFFFFF);
            bg.setStroke(Math.max(2, Math.round(3 * s)), focus ? 0xE6FFFFFF : 0x00FFFFFF);
        }
    }

    @SuppressWarnings("deprecation")
    private String gather() {
        rows.clear();
        boolean connected = false;
        String type = "none", cur = null;
        try {
            ConnectivityManager cm = (ConnectivityManager) act.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            connected = ni != null && ni.isConnected();
            if (connected) type = ni.getType() == ConnectivityManager.TYPE_ETHERNET ? "ethernet" : ni.getType() == ConnectivityManager.TYPE_WIFI ? "wifi" : "other";
        } catch (Exception ignored) {
        }
        WifiManager wm = (WifiManager) act.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if ("ethernet".equals(type)) {
            Row r = new Row();
            r.name = "Ethernet";
            r.cur = true;
            r.eth = true;
            rows.add(r);
        }
        if ("wifi".equals(type) && wm != null) {
            try {
                WifiInfo wi = wm.getConnectionInfo();
                cur = wi == null ? null : clean(wi.getSSID());
            } catch (Exception ignored) {
            }
            Row r = new Row();
            r.name = cur != null ? cur : "Connected network";
            r.cur = true;
            r.lock = true;
            rows.add(r);
        }
        if (wm != null && hasLocation()) {
            try {
                List<ScanResult> res = new ArrayList<>(wm.getScanResults());
                Collections.sort(res, new Comparator<ScanResult>() {
                    @Override
                    public int compare(ScanResult a, ScanResult b) {
                        return b.level - a.level;
                    }
                });
                Set<String> seen = new HashSet<>();
                if (cur != null) seen.add(cur);
                for (ScanResult sr : res) {
                    if (rows.size() >= 4) break;
                    String n = clean(sr.SSID);
                    if (n == null || !seen.add(n)) continue;
                    Row r = new Row();
                    r.name = n;
                    String cap = sr.capabilities == null ? "" : sr.capabilities;
                    r.lock = cap.contains("WPA") || cap.contains("WEP") || cap.contains("PSK") || cap.contains("SAE");
                    rows.add(r);
                }
                wm.startScan();
            } catch (Exception ignored) {
            }
        }
        Row other = new Row();
        other.name = "Other network…";
        other.other = true;
        rows.add(other);
        if (!connected) return "Not connected";
        return "ethernet".equals(type) ? "Ethernet" : "Connected";
    }

    boolean hasLocation() {
        if (Build.VERSION.SDK_INT < 23) return true;
        return act.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || act.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private static String clean(String v) {
        if (v == null) return null;
        v = v.trim();
        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) v = v.substring(1, v.length() - 1);
        if (v.isEmpty() || "<unknown ssid>".equals(v) || "0x".equals(v)) return null;
        return v;
    }

    // ---------------------------------------------------------------- helpers

    private TextView text(String t, Typeface tf, float size, int color) {
        TextView v = new TextView(act);
        v.setText(t);
        v.setTypeface(tf);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * s);
        v.setTextColor(color);
        v.setLetterSpacing(-0.4f / size);
        v.setIncludeFontPadding(false);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private ImageView icon(int res) {
        ImageView v = new ImageView(act);
        v.setImageResource(res);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return v;
    }

    private void add(View v, float x, float y, float w, float h) {
        addTo(this, v, x, y, w, h);
    }

    private void addTo(ViewGroup p, View v, float x, float y, float w, float h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(w * s), Math.round(h * s));
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.leftMargin = Math.round(x * s);
        lp.topMargin = Math.round(y * s);
        p.addView(v, lp);
    }
}
