package com.clive.tvhome;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TV Home: Home and All apps, drawn with native views so they stay smooth on TV hardware.
 * Layout follows the TV Home Launcher design on a 1920 x 1080 grid, scaled to the screen.
 * Settings, Wallpaper, Apps on Home and the Wi-Fi panel open in {@link SettingsActivity}.
 */
public class MainActivity extends Activity {

    private static final int DOCK = 0, TOP = 1, GRID = 2;
    private static final int COLS = 5;
    private static final float PITCH = 352, ROW_H = 202;
    private static final String[] WALL_IDS = {"misty", "cove", "forest", "night"};
    private static final float[] WALL_VEIL = {.2f, .12f, .12f, .12f};

    private float s;
    private Typeface manropeSemi, manropeBold, manropeMedium, nunito;
    private SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private ImageView photo, veil, cueDown, cueUp;
    private ImageView btnWifi, btnSettings;
    private GradientDrawable ringWifi, ringSettings;
    private FrameLayout slide, dockGroup, gridClip, grid;
    private GlassView dockGlass;
    private ClockView clock, pill;
    private WifiPanel wifi;
    private ObjectAnimator clockFade, pillFade;

    private List<Catalog.Item> items = new ArrayList<>();
    private final List<TileView> dockTiles = new ArrayList<>(), gridTiles = new ArrayList<>();
    private final Map<String, Bitmap> brandCache = new HashMap<>();
    private String wallId, homeJson;
    private int loadGen;

    private boolean apps, lock;
    private int zone = DOCK, dockIdx, gridIdx, topIdx = 1, gridFirst = -1;

    // ------------------------------------------------------------------ setup

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUi();
        prefs = getSharedPreferences(Catalog.PREFS, MODE_PRIVATE);

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        s = Math.min(dm.widthPixels / 1920f, dm.heightPixels / 1080f);

        manropeSemi = font("fonts/manrope_semibold.ttf", Typeface.DEFAULT);
        manropeBold = font("fonts/manrope_bold.ttf", Typeface.DEFAULT_BOLD);
        manropeMedium = font("fonts/manrope_medium.ttf", Typeface.DEFAULT);
        nunito = font("fonts/nunito_extrabold.ttf", Typeface.DEFAULT_BOLD);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF1D2A30);
        root.setClipChildren(false);
        setContentView(root);

        photo = add(root, new ImageView(this), 0, 0, 1920, 1080);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);

        View shadeTop = add(root, new View(this), 0, 0, 1920, 300);
        shadeTop.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x52040A0C, 0x24040A0C, 0x00040A0C}));
        View shadeBot = add(root, new View(this), 0, 560, 1920, 520);
        shadeBot.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00040A0C, 0x5A040A0C, 0x85040A0C}));

        veil = add(root, new ImageView(this), 0, 0, 1920, 1080);
        veil.setScaleType(ImageView.ScaleType.FIT_XY);
        veil.setColorFilter(new PorterDuffColorFilter(0x75060A0E, PorterDuff.Mode.SRC_ATOP));
        veil.setAlpha(0f);

        // Everything that slides up for All apps
        slide = add(root, new FrameLayout(this), 0, 0, 1920, 1760);
        slide.setClipChildren(false);

        dockGroup = add(slide, new FrameLayout(this), 35, 650, 1850, 330);
        dockGroup.setClipChildren(false);
        dockGlass = add(dockGroup, new GlassView(this, s, 65, 711, 1790, 232, 56), 30, 61, 1790, 232);

        cueDown = add(slide, icon(R.drawable.ic_chev_down), 906, 960, 108, 108);
        cueDown.setAlpha(.66f);

        TextView allLabel = add(slide, new TextView(this), 95, 984, -1, -1);
        allLabel.setText("All apps");
        allLabel.setTypeface(manropeSemi);
        allLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, 30 * s);
        allLabel.setLetterSpacing(-0.4f / 30f);
        allLabel.setTextColor(0xE0FFFFFF);
        allLabel.setShadowLayer(3 * s, 0, s, 0x4D000000);
        allLabel.setIncludeFontPadding(false);

        gridClip = add(slide, new FrameLayout(this), 59, 1028, 1802, 712);
        gridClip.setClipChildren(true);
        grid = add(gridClip, new FrameLayout(this), 0, 0, 1802, 712);
        grid.setClipChildren(false);

        // Clock: big on Home, flies into the pill on All apps
        clock = new ClockView(this, s, nunito, false);
        root.addView(clock, wrap());
        pill = new ClockView(this, s, nunito, true);
        FrameLayout.LayoutParams plp = wrap();
        plp.leftMargin = Math.round(65 * s);
        plp.topMargin = Math.round(72 * s);
        root.addView(pill, plp);
        pill.setAlpha(0f);

        // Top bar
        ringWifi = ring();
        ringSettings = ring();
        btnWifi = add(root, icon(R.drawable.ic_wifi), 1610, 60, 108, 108);
        btnWifi.setBackground(new InsetDrawable(ringWifi, Math.round(6 * s)));
        btnSettings = add(root, icon(R.drawable.ic_settings), 1702, 60, 108, 108);
        btnSettings.setBackground(new InsetDrawable(ringSettings, Math.round(6 * s)));
        cueUp = add(root, icon(R.drawable.ic_chev_up), 906, 60, 108, 108);
        cueUp.setAlpha(0f);

        wifi = add(root, new WifiPanel(this, s, manropeSemi, manropeMedium), 1258, 176, 540, 300);

        applyWallpaper(true);
        tick();
        updateNetwork();
        reload();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pf.addDataScheme("package");
        registerReceiver(packages, pf);
        IntentFilter tf = new IntentFilter();
        tf.addAction(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(time, tf);
        registerReceiver(network, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    private Typeface font(String path, Typeface fallback) {
        try {
            return Typeface.createFromAsset(getAssets(), path);
        } catch (Exception e) {
            return fallback;
        }
    }

    private FrameLayout.LayoutParams wrap() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        return lp;
    }

    /** Place a view at design coordinates (1920x1080 grid); w/h < 0 means wrap content. */
    private <T extends View> T add(ViewGroup parent, T v, float x, float y, float w, float h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                w < 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : Math.round(w * s),
                h < 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : Math.round(h * s));
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.leftMargin = Math.round(x * s);
        lp.topMargin = Math.round(y * s);
        parent.addView(v, lp);
        return v;
    }

    private ImageView icon(int res) {
        ImageView v = new ImageView(this);
        v.setImageResource(res);
        int pad = Math.round(29 * s);   // 108 box, 50 glyph
        v.setPadding(pad, pad, pad, pad);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setImageAlpha(168);           // design opacity .66
        return v;
    }

    private GradientDrawable ring() {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setStroke(Math.max(2, Math.round(3 * s)), 0xFFFFFFFF);
        g.setColor(0x00000000);
        g.setAlpha(0);
        return g;
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    // ------------------------------------------------------------------ data

    private void reload() {
        final int gen = ++loadGen;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Catalog.Item> loaded = Catalog.load(MainActivity.this);
                for (Catalog.Item it : loaded) if (it.brand != null) brandTile(it.brand);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != loadGen || isDestroyed()) return;
                        items = loaded;
                        buildTiles();
                    }
                });
            }
        }, "catalog").start();
    }

    private Bitmap brandTile(String id) {
        synchronized (brandCache) {
            if (brandCache.containsKey(id)) return brandCache.get(id);
        }
        Bitmap b = null;
        try (InputStream in = getAssets().open("brands/" + id + ".png")) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            b = BitmapFactory.decodeStream(in, null, o);
        } catch (Exception ignored) {
        }
        synchronized (brandCache) {
            brandCache.put(id, b);
        }
        return b;
    }

    private Bitmap asset(String path, int targetW) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = getAssets().open(path)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            int sample = 1;
            while (o.outWidth / (sample * 2) >= targetW) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            d.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream in = getAssets().open(path)) {
                return BitmapFactory.decodeStream(in, null, d);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void applyWallpaper(boolean force) {
        String id = prefs.getString("wallpaper", "misty");
        int idx = 0;
        for (int i = 0; i < WALL_IDS.length; i++) if (WALL_IDS[i].equals(id)) idx = i;
        id = WALL_IDS[idx];
        if (!force && id.equals(wallId)) return;
        wallId = id;
        int screenW = Math.round(1920 * s);
        photo.setImageBitmap(asset("wallpapers/" + id + "-photo.jpg", screenW));
        veil.setImageBitmap(asset("wallpapers/" + id + "-soft.jpg", 960));
        int tint = (Math.round(WALL_VEIL[idx] * 255) << 24) | 0x080E12;
        Bitmap glass = asset("wallpapers/" + id + "-glass.jpg", 960);
        dockGlass.set(glass, tint);
        if (wifi != null) wifi.setGlass(glass);
    }

    private void buildTiles() {
        Map<String, Catalog.Item> byId = new HashMap<>();
        for (Catalog.Item it : items) byId.put(it.id, it);
        List<String> home = Catalog.homeIds(this, items);
        homeJson = prefs.getString("home", null);

        for (TileView t : dockTiles) dockGroup.removeView(t);
        dockTiles.clear();
        int i = 0;
        for (String id : home) {
            Catalog.Item it = byId.get(id);
            if (it == null) continue;
            dockTiles.add(add(dockGroup, tile(it), 60 + i * PITCH, 91, TileView.W, TileView.H));
            i++;
        }

        grid.removeAllViews();
        gridTiles.clear();
        for (int g = 0; g < items.size(); g++) {
            float x = 36 + (g % COLS) * PITCH, y = 36 + (g / COLS) * ROW_H;
            gridTiles.add(add(grid, tile(items.get(g)), x, y, TileView.W, TileView.H));
        }
        int rows = (items.size() + COLS - 1) / COLS;
        ViewGroup.LayoutParams lp = grid.getLayoutParams();
        lp.height = Math.round((36 + rows * ROW_H + 120) * s);
        grid.setLayoutParams(lp);
        gridFirst = -1;

        dockIdx = Math.max(0, Math.min(dockIdx, dockTiles.size() - 1));
        gridIdx = Math.max(0, Math.min(gridIdx, gridTiles.size() - 1));
        if (dockTiles.isEmpty() && zone == DOCK) zone = TOP;
        paint(false);
    }

    private TileView tile(Catalog.Item it) {
        return new TileView(this, s, it, it.brand != null ? brandTile(it.brand) : null, manropeBold, manropeSemi);
    }

    // ----------------------------------------------------------------- focus

    private void paint(boolean animate) {
        for (int i = 0; i < dockTiles.size(); i++) dockTiles.get(i).setOn(zone == DOCK && i == dockIdx, animate);
        for (int i = 0; i < gridTiles.size(); i++) gridTiles.get(i).setOn(zone == GRID && i == gridIdx, animate);
        boolean panel = wifi != null && wifi.isOpen();
        boolean wifiOn = zone == TOP && topIdx == 0 && !panel, setOn = zone == TOP && topIdx == 1 && !panel;
        ringWifi.setAlpha(wifiOn ? 255 : 0);
        ringSettings.setAlpha(setOn ? 255 : 0);
        btnWifi.setImageAlpha(wifiOn || panel ? 255 : 168);
        btnSettings.setImageAlpha(setOn ? 255 : 168);

        if (zone == GRID && apps) {
            int first = Math.max(0, gridIdx / COLS - 1);
            float top = first > 0 ? first * ROW_H + 8 : 0;
            grid.animate().translationY(-top * s).setDuration(animate ? 420 : 0).setInterpolator(TileView.EASE).start();
            if (first != gridFirst) {
                gridFirst = first;
                for (int i = 0; i < gridTiles.size(); i++) {
                    gridTiles.get(i).setVisibility(i < first * COLS ? View.INVISIBLE : View.VISIBLE);
                }
            }
        }
    }

    // ----------------------------------------------------------------- views

    private boolean beginTransition() {
        if (lock) return false;
        lock = true;
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                lock = false;
            }
        }, 660);
        return true;
    }

    private void goApps(int col) {
        if (gridTiles.isEmpty() || !beginTransition()) return;
        apps = true;
        zone = GRID;
        gridIdx = Math.min(col, gridTiles.size() - 1);
        slide.animate().translationY(-700 * s).setDuration(620).setInterpolator(TileView.EASE).start();
        veil.animate().alpha(1f).setDuration(620).setInterpolator(TileView.EASE).start();
        dockGroup.animate().alpha(0f).translationY(-40 * s).setDuration(450).setInterpolator(TileView.EASE).withLayer().start();
        cueDown.animate().alpha(0f).setDuration(300).start();
        cueUp.animate().alpha(.66f).setDuration(300).start();
        clock.animate().scaleX(.192f).scaleY(.192f).translationX(clockDx()).translationY(-196 * s)
                .setDuration(620).setInterpolator(TileView.EASE).withLayer().start();
        fade(true);
        paint(true);
    }

    private void goHome(boolean force) {
        if (!apps) {
            zone = dockTiles.isEmpty() ? TOP : DOCK;
            paint(true);
            return;
        }
        if (!force && !beginTransition()) return;
        apps = false;
        zone = dockTiles.isEmpty() ? TOP : DOCK;
        slide.animate().translationY(0).setDuration(620).setInterpolator(TileView.EASE).start();
        veil.animate().alpha(0f).setDuration(620).setInterpolator(TileView.EASE).start();
        dockGroup.animate().alpha(1f).translationY(0).setDuration(450).setInterpolator(TileView.EASE).withLayer().start();
        cueDown.animate().alpha(.66f).setDuration(300).start();
        cueUp.animate().alpha(0f).setDuration(300).start();
        clock.animate().scaleX(1f).scaleY(1f).translationX(0).translationY(0)
                .setDuration(620).setInterpolator(TileView.EASE).withLayer().start();
        fade(false);
        paint(true);
    }

    /** Cross-fade big clock and pill at the end of the flight (design timings). */
    private void fade(boolean toApps) {
        if (clockFade != null) clockFade.cancel();
        if (pillFade != null) pillFade.cancel();
        clockFade = ObjectAnimator.ofFloat(clock, View.ALPHA, clock.getAlpha(), toApps ? 0f : 1f);
        clockFade.setDuration(toApps ? 160 : 140);
        clockFade.setStartDelay(toApps ? 430 : 0);
        pillFade = ObjectAnimator.ofFloat(pill, View.ALPHA, pill.getAlpha(), toApps ? 1f : 0f);
        pillFade.setDuration(toApps ? 200 : 120);
        pillFade.setStartDelay(toApps ? 420 : 0);
        clockFade.start();
        pillFade.start();
    }

    /** Horizontal move that puts the big clock's text where the pill's text sits (x = 95). */
    private float clockDx() {
        return 95 * s - (960 * s - clock.textWidth() / 2f);
    }

    // ---------------------------------------------------------------- clock

    private void tick() {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR);
        if (h == 0) h = 12;
        String t = h + ":" + (c.get(Calendar.MINUTE) < 10 ? "0" : "") + c.get(Calendar.MINUTE);
        clock.setText(t);
        pill.setText(t);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) clock.getLayoutParams();
        lp.leftMargin = Math.round(960 * s - clock.textWidth() / 2f - clock.textLeftInset());
        lp.topMargin = Math.round(280 * s - clock.textTopInset());
        clock.setLayoutParams(lp);
        clock.setPivotX(clock.textLeftInset());
        clock.setPivotY(clock.textTopInset());
        if (apps) clock.setTranslationX(clockDx());
    }

    private final BroadcastReceiver time = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            tick();
        }
    };

    // -------------------------------------------------------------- network

    @SuppressWarnings("deprecation")
    private void updateNetwork() {
        int res = R.drawable.ic_wifi_off;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            if (ni != null && ni.isConnected()) {
                res = ni.getType() == ConnectivityManager.TYPE_ETHERNET ? R.drawable.ic_ethernet : R.drawable.ic_wifi;
            }
        } catch (Exception ignored) {
        }
        btnWifi.setImageResource(res);
    }

    private final BroadcastReceiver network = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateNetwork();
            wifi.refresh();
        }
    };

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            reload();
        }
    };

    // ----------------------------------------------------------------- keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        String k = keyName(e.getKeyCode());
        if (k == null) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) onKey(k);
        return true;
    }

    private static String keyName(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: return "left";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "right";
            case KeyEvent.KEYCODE_DPAD_UP: return "up";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "down";
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A: return "ok";
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE: return "back";
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS: return "menu";
            default: return null;
        }
    }

    private void onKey(String k) {
        if (wifi.isOpen()) {
            wifi.key(k);
            paint(true);
            return;
        }
        if (k.equals("back")) {
            if (apps) goHome(false);
            return;
        }
        if (k.equals("menu")) {
            openSettings("apps", 0);
            return;
        }
        if (zone == DOCK) {
            int n = dockTiles.size();
            switch (k) {
                case "left": if (dockIdx > 0) dockIdx--; break;
                case "right": if (dockIdx < n - 1) dockIdx++; break;
                case "up": zone = TOP; break;
                case "down": goApps(Math.min(dockIdx, COLS - 1)); return;
                case "ok": if (n > 0) Catalog.launch(this, dockTiles.get(dockIdx).item); return;
            }
        } else if (zone == TOP) {
            switch (k) {
                case "left": topIdx = 0; break;
                case "right": topIdx = 1; break;
                case "down":
                    if (apps) zone = GRID;
                    else if (!dockTiles.isEmpty()) zone = DOCK;
                    else { goApps(0); return; }
                    break;
                case "ok":
                    if (topIdx == 0) openWifi(); else openSettings(null, 0);
                    return;
            }
        } else if (zone == GRID) {
            int g = gridTiles.size(), i = gridIdx, c = i % COLS;
            switch (k) {
                case "left": if (c > 0) i--; break;
                case "right": if (c < COLS - 1 && i + 1 < g) i++; break;
                case "up": if (i >= COLS) i -= COLS; else { goHome(false); return; } break;
                case "down":
                    if (i + COLS < g) i += COLS;
                    else if (i / COLS < (g - 1) / COLS) i = g - 1;
                    break;
                case "ok": if (g > 0) Catalog.launch(this, gridTiles.get(i).item); return;
            }
            gridIdx = i;
        }
        paint(true);
    }

    // ------------------------------------------------------- settings / wifi

    private void openSettings(String screen, int focus) {
        Intent i = new Intent(this, SettingsActivity.class).putExtra("mode", "settings").putExtra("f", focus);
        if (screen != null) i.putExtra("screen", screen);
        startActivity(i);
    }

    private void openWifi() {
        // Android only reveals Wi-Fi names to apps with location permission; ask once.
        if (android.os.Build.VERSION.SDK_INT >= 23 && !wifi.hasLocation() && !prefs.getBoolean("askedLocation", false)) {
            prefs.edit().putBoolean("askedLocation", true).apply();
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION}, 7);
        }
        wifi.open();
        paint(true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        wifi.refresh();
    }

    private boolean isDefaultHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && getPackageName().equals(ri.activityInfo.packageName);
    }

    // ------------------------------------------------------------- lifecycle

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        tick();
        updateNetwork();
        applyWallpaper(false);
        String h = prefs.getString("home", null);
        if (!items.isEmpty() && (h == null ? homeJson != null : !h.equals(homeJson))) buildTiles();
        // First run: offer to make TV Home the Home button.
        if (prefs.getString("homeAsked", null) == null && !isDefaultHome()) {
            prefs.edit().putString("homeAsked", "1").apply();   // string: the Settings page shares this key
            ui.postDelayed(new Runnable() {
                @Override
                public void run() {
                    openSettings(null, 3);
                }
            }, 600);
        }
    }

    /** Home pressed while already here: back to the main Home view. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        wifi.close();
        goHome(true);
    }

    @Override
    public void onBackPressed() {
        if (wifi.isOpen()) { wifi.close(); paint(true); return; }
        if (apps) goHome(false);
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(packages); } catch (Exception ignored) { }
        try { unregisterReceiver(time); } catch (Exception ignored) { }
        try { unregisterReceiver(network); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
