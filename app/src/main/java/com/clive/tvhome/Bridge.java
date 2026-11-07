package com.clive.tvhome;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Everything the page knows about the TV comes through here, read live from the
 * device: installed apps with their own banners/icons, the TV's inputs, the
 * network it is on and the model/Android version. Exposed to JS as window.Native.
 */
public class Bridge {

    private static final String LEANBACK = "android.intent.category.LEANBACK_LAUNCHER";

    private final Activity act;
    private final WebView web;
    private final PackageManager pm;
    private final SharedPreferences prefs;
    private String appsCache;

    Bridge(Activity act, WebView web) {
        this.act = act;
        this.web = web;
        this.pm = act.getPackageManager();
        this.prefs = act.getSharedPreferences("tvhome", Context.MODE_PRIVATE);
    }

    synchronized void invalidateApps() {
        appsCache = null;
    }

    // ---------------------------------------------------------------- apps

    /** Every launchable app on the TV, TV-optimised (leanback) entries first per package. */
    @JavascriptInterface
    public synchronized String apps() {
        if (appsCache != null) return appsCache;
        Map<String, JSONObject> found = new LinkedHashMap<>();
        collect(found, new Intent(Intent.ACTION_MAIN).addCategory(LEANBACK), true);
        collect(found, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), false);

        List<JSONObject> list = new ArrayList<>(found.values());
        Collections.sort(list, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                return a.optString("n").compareToIgnoreCase(b.optString("n"));
            }
        });
        JSONArray out = new JSONArray();
        for (JSONObject o : list) out.put(o);
        appsCache = out.toString();
        return appsCache;
    }

    private void collect(Map<String, JSONObject> found, Intent query, boolean tv) {
        List<ResolveInfo> list;
        try {
            list = pm.queryIntentActivities(query, 0);
        } catch (Exception e) {
            return;
        }
        String self = act.getPackageName();
        for (ResolveInfo ri : list) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || self.equals(ai.packageName) || found.containsKey(ai.packageName)) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("pkg", ai.packageName);
                o.put("cls", ai.name);
                o.put("n", String.valueOf(ri.loadLabel(pm)).trim());
                o.put("tv", tv);
                Drawable banner = null;
                if (Build.VERSION.SDK_INT >= 20) {
                    banner = ai.loadBanner(pm);
                    if (banner == null && ai.applicationInfo != null) banner = ai.applicationInfo.loadBanner(pm);
                }
                if (banner != null) {
                    int w = Math.max(320, Math.min(640, banner.getIntrinsicWidth()));
                    o.put("banner", toDataUri(banner, w, Math.round(w * 9f / 16f)));
                } else {
                    o.put("icon", toDataUri(ri.loadIcon(pm), 192, 192));
                }
                found.put(ai.packageName, o);
            } catch (Exception ignored) {
                // one bad app must not hide the rest
            }
        }
    }

    private static String toDataUri(Drawable d, int w, int h) {
        if (d == null) return null;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, w, h);
        d.draw(c);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, bos);
        b.recycle();
        return "data:image/png;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
    }

    @JavascriptInterface
    public void launch(final String pkg, final String cls) {
        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent it = null;
                if (Build.VERSION.SDK_INT >= 21) it = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (it == null) it = pm.getLaunchIntentForPackage(pkg);
                if (it == null && cls != null && cls.length() > 0) {
                    it = new Intent(Intent.ACTION_MAIN).setClassName(pkg, cls);
                }
                if (it == null) {
                    toast("Can't open this app");
                    return;
                }
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                try {
                    act.startActivity(it);
                } catch (Exception e) {
                    toast("Can't open this app");
                }
            }
        });
    }

    // -------------------------------------------------------------- inputs

    /** HDMI / AV / component inputs the TV exposes through Android's TV input framework. */
    @JavascriptInterface
    public String inputs() {
        JSONArray out = new JSONArray();
        if (Build.VERSION.SDK_INT < 21) return out.toString();
        try {
            TvInputManager tim = (TvInputManager) act.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) return out.toString();
            for (TvInputInfo i : tim.getTvInputList()) {
                if (!i.isPassthroughInput()) continue;          // tuner/Live TV stays as its app
                if (i.getParentId() != null) continue;           // CEC devices behind an HDMI port
                if (Build.VERSION.SDK_INT >= 23 && i.isHidden(act)) continue;
                CharSequence label = null;
                if (Build.VERSION.SDK_INT >= 23) label = i.loadCustomLabel(act);
                if (label == null || label.length() == 0) label = i.loadLabel(act);
                JSONObject o = new JSONObject();
                o.put("id", i.getId());
                o.put("n", String.valueOf(label).trim());
                o.put("type", typeName(i.getType()));
                out.put(o);
            }
        } catch (Exception ignored) {
            // vendor TVs sometimes restrict this; the launcher simply shows no inputs
        }
        return out.toString();
    }

    private static String typeName(int t) {
        switch (t) {
            case TvInputInfo.TYPE_HDMI: return "hdmi";
            case TvInputInfo.TYPE_COMPOSITE: return "av";
            case TvInputInfo.TYPE_SVIDEO: return "svideo";
            case TvInputInfo.TYPE_SCART: return "scart";
            case TvInputInfo.TYPE_COMPONENT: return "component";
            case TvInputInfo.TYPE_VGA: return "vga";
            case TvInputInfo.TYPE_DVI: return "dvi";
            case TvInputInfo.TYPE_DISPLAY_PORT: return "dp";
            default: return "other";
        }
    }

    @JavascriptInterface
    public void openInput(final String inputId) {
        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
                    Intent it = new Intent(Intent.ACTION_VIEW, uri);
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    act.startActivity(it);
                } catch (Exception e) {
                    toast("This TV can't switch input from here");
                }
            }
        });
    }

    // ------------------------------------------------------------ settings

    @JavascriptInterface
    public void openSettings(final String which) {
        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String action;
                switch (which == null ? "" : which) {
                    case "wifi": action = Settings.ACTION_WIFI_SETTINGS; break;
                    case "display": action = Settings.ACTION_DISPLAY_SETTINGS; break;
                    case "sound": action = Settings.ACTION_SOUND_SETTINGS; break;
                    case "accessibility": action = Settings.ACTION_ACCESSIBILITY_SETTINGS; break;
                    case "about": action = Settings.ACTION_DEVICE_INFO_SETTINGS; break;
                    case "home": action = Build.VERSION.SDK_INT >= 21 ? Settings.ACTION_HOME_SETTINGS : Settings.ACTION_SETTINGS; break;
                    default: action = Settings.ACTION_SETTINGS; break;
                }
                if (!start(new Intent(action)) && !start(new Intent(Settings.ACTION_SETTINGS))) {
                    toast("Settings aren't available");
                }
            }
        });
    }

    private boolean start(Intent it) {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            act.startActivity(it);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------- network

    @JavascriptInterface
    public String network() {
        JSONObject o = new JSONObject();
        try {
            ConnectivityManager cm = (ConnectivityManager) act.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            boolean connected = ni != null && ni.isConnected();
            o.put("connected", connected);
            String type = "none";
            if (connected) {
                if (ni.getType() == ConnectivityManager.TYPE_ETHERNET) type = "ethernet";
                else if (ni.getType() == ConnectivityManager.TYPE_WIFI) type = "wifi";
                else type = "other";
            }
            o.put("type", type);
            WifiManager wm = (WifiManager) act.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && "wifi".equals(type)) {
                WifiInfo wi = wm.getConnectionInfo();
                if (wi != null) {
                    String ssid = cleanSsid(wi.getSSID());
                    if (ssid != null) o.put("ssid", ssid);
                    o.put("level", WifiManager.calculateSignalLevel(wi.getRssi(), 4));
                    int ip = wi.getIpAddress();
                    if (ip != 0) {
                        o.put("ip", String.format(Locale.US, "%d.%d.%d.%d",
                                ip & 0xff, (ip >> 8) & 0xff, (ip >> 16) & 0xff, (ip >> 24) & 0xff));
                    }
                }
            }
            o.put("location", hasLocation());
        } catch (Exception ignored) {
        }
        return o.toString();
    }

    /** Nearby networks, strongest first; "null" when Android withholds them (no location permission). */
    @JavascriptInterface
    public String wifiList() {
        if (!hasLocation()) return "null";
        JSONArray out = new JSONArray();
        try {
            WifiManager wm = (WifiManager) act.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return out.toString();
            List<ScanResult> res = new ArrayList<>(wm.getScanResults());
            Collections.sort(res, new Comparator<ScanResult>() {
                @Override
                public int compare(ScanResult a, ScanResult b) {
                    return b.level - a.level;
                }
            });
            Set<String> seen = new HashSet<>();
            for (ScanResult r : res) {
                String ssid = cleanSsid(r.SSID);
                if (ssid == null || !seen.add(ssid)) continue;
                JSONObject o = new JSONObject();
                o.put("n", ssid);
                String cap = r.capabilities == null ? "" : r.capabilities;
                o.put("lock", cap.contains("WPA") || cap.contains("WEP") || cap.contains("PSK") || cap.contains("SAE"));
                out.put(o);
                if (out.length() >= 8) break;
            }
        } catch (Exception ignored) {
        }
        return out.toString();
    }

    @JavascriptInterface
    @SuppressWarnings("deprecation")
    public void scanWifi() {
        try {
            WifiManager wm = (WifiManager) act.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) wm.startScan();
        } catch (Exception ignored) {
        }
    }

    @JavascriptInterface
    public boolean hasLocation() {
        if (Build.VERSION.SDK_INT < 23) return true;
        return act.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || act.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @JavascriptInterface
    public void askLocation() {
        if (Build.VERSION.SDK_INT < 23 || hasLocation()) return;
        if (prefs.getBoolean("askedLocation", false)) return; // ask once; Wi-Fi settings still work without it
        prefs.edit().putBoolean("askedLocation", true).apply();
        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                act.requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, 7);
            }
        });
    }

    private static String cleanSsid(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) s = s.substring(1, s.length() - 1);
        if (s.length() == 0 || "<unknown ssid>".equals(s) || "0x".equals(s)) return null;
        return s;
    }

    // -------------------------------------------------------------- device

    @JavascriptInterface
    public String device() {
        JSONObject o = new JSONObject();
        try {
            DisplayMetrics dm = new DisplayMetrics();
            act.getWindowManager().getDefaultDisplay().getRealMetrics(dm);
            o.put("manufacturer", Build.MANUFACTURER);
            o.put("brand", Build.BRAND);
            o.put("model", Build.MODEL);
            o.put("release", Build.VERSION.RELEASE);
            o.put("sdk", Build.VERSION.SDK_INT);
            o.put("w", dm.widthPixels);
            o.put("h", dm.heightPixels);
            o.put("defaultHome", isDefaultHome());
        } catch (Exception ignored) {
        }
        return o.toString();
    }

    boolean isDefaultHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && act.getPackageName().equals(ri.activityInfo.packageName);
    }

    // ------------------------------------------------------- Home button

    /** {"default": TV Home is the system Home app} */
    @JavascriptInterface
    public String homeStatus() {
        JSONObject o = new JSONObject();
        try {
            o.put("default", isDefaultHome());
        } catch (Exception ignored) {
        }
        return o.toString();
    }

    /** Ask Android to make TV Home the Home app; if the TV can't, offer Button Mapper from Google Play. */
    @JavascriptInterface
    public void makeDefaultHome() {
        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isDefaultHome()) {
                    toast("TV Home is already your home screen");
                    return;
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    try {
                        android.app.role.RoleManager rm = act.getSystemService(android.app.role.RoleManager.class);
                        if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)
                                && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                            act.startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), SettingsActivity.REQ_HOME_ROLE);
                            return;
                        }
                    } catch (Exception ignored) {
                        // fall through to Button Mapper
                    }
                }
                openButtonMapper();
            }
        });
    }

    /** Button Mapper (Google Play) can send the remote's Home button to TV Home on TVs with no launcher choice. */
    void openButtonMapper() {
        Toast.makeText(act, "Install Button Mapper, then map Home to TV Home", Toast.LENGTH_LONG).show();
        if (!start(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=flar2.homebutton")))) {
            start(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=flar2.homebutton")));
        }
    }

    /** Close the Settings / Wi-Fi overlay and return to Home. */
    @JavascriptInterface
    public void close() {
        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                act.finish();
            }
        });
    }

    // --------------------------------------------------------------- prefs

    @JavascriptInterface
    public String get(String key) {
        return prefs.getString(key, null);
    }

    @JavascriptInterface
    public void set(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    private void toast(final String msg) {
        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(act, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
