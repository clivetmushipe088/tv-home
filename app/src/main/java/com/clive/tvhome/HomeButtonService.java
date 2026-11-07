package com.clive.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Makes the remote's Home button open TV Home on TVs that don't let you pick a default
 * Home app. When the TV's own launcher comes to the front (Home pressed, Back out of an
 * app, power-on) this brings TV Home up in its place. It only watches which app is in
 * front; it reads no screen content and no keys.
 */
public class HomeButtonService extends AccessibilityService {

    static final String PREFS = "tvhome";
    /** Set when the user opens the stock launcher on purpose from TV Home. */
    static final String ALLOW_STOCK_UNTIL = "allowStockUntil";

    private final Set<String> stockLaunchers = new HashSet<>();
    private long refreshedAt;
    private long lastRedirect;

    @Override
    protected void onServiceConnected() {
        refresh();
    }

    private void refresh() {
        stockLaunchers.clear();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> list = getPackageManager().queryIntentActivities(home, 0);
        for (ResolveInfo ri : list) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            // Boot placeholder and setup wizard also answer HOME; never fight those.
            String p = pkg.toLowerCase();
            if (p.contains("settings") || p.contains("setup") || p.contains("provision")) continue;
            stockLaunchers.add(pkg);
        }
        refreshedAt = SystemClock.elapsedRealtime();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        long now = SystemClock.elapsedRealtime();
        if (now - refreshedAt > 60_000) refresh();
        if (!stockLaunchers.contains(pkg.toString())) return;

        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (System.currentTimeMillis() < sp.getLong(ALLOW_STOCK_UNTIL, 0)) return;
        if (now - lastRedirect < 1500) return;
        lastRedirect = now;

        Intent it = new Intent(this, MainActivity.class);
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivity(it);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onInterrupt() {
    }
}
