package com.clive.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** What's on this TV: every launchable app (with its own banner/icon) and its inputs. */
final class Catalog {

    static final String PREFS = "tvhome";
    static final int MAX_HOME = 5;
    private static final String LEANBACK = "android.intent.category.LEANBACK_LAUNCHER";

    static final class Item {
        String id;       // package name, or "in:<inputId>" for an input
        String kind;     // "app" | "input"
        String pkg, cls, label, inputId, brand;
        boolean tv;
        Drawable banner, icon;
    }

    /** Package-name rules mapping apps to the design's brand tiles (assets/brands/<id>.png). */
    private static final Object[][] RULES = {
            {"youtube", Pattern.compile("^com\\.google\\.android\\.youtube(\\.tv|\\.googletv)?$")},
            {"netflix", Pattern.compile("^com\\.netflix\\.(ninja|mediaclient)$")},
            {"spotify", Pattern.compile("^com\\.spotify\\.(tv\\.android|music)$")},
            {"dstv", Pattern.compile("dstv", Pattern.CASE_INSENSITIVE)},
            {"prime", Pattern.compile("^com\\.amazon\\.(amazonvideo\\.livingroom|avod\\.thirdpartyclient)$")},
            {"disney", Pattern.compile("^com\\.disney\\.disneyplus")},
            {"stremio", Pattern.compile("^com\\.stremio\\.")},
            {"hbomax", Pattern.compile("^(com\\.hbo\\.|com\\.wbd\\.stream)")},
            {"tubi", Pattern.compile("^com\\.tubitv")},
            {"twitch", Pattern.compile("^tv\\.twitch\\.")},
            {"plex", Pattern.compile("^com\\.plexapp\\.")},
            {"vlc", Pattern.compile("^org\\.videolan\\.vlc")},
            {"soundcloud", Pattern.compile("^com\\.soundcloud\\.")},
            {"deezer", Pattern.compile("^deezer\\.android\\.")},
            {"chrome", Pattern.compile("^com\\.(android|chrome)\\.chrome")},
            {"vimeo", Pattern.compile("^com\\.vimeo\\.")},
            {"crunchyroll", Pattern.compile("^com\\.crunchyroll\\.")},
            {"tiktok", Pattern.compile("^(com\\.zhiliaoapp\\.musically|com\\.ss\\.android\\.ugc\\.)")},
            {"steam", Pattern.compile("^com\\.valvesoftware\\.")},
            {"photos", Pattern.compile("^com\\.google\\.android\\.apps\\.photos")},
    };

    static String brandFor(String pkg) {
        for (Object[] r : RULES) if (((Pattern) r[1]).matcher(pkg).find()) return (String) r[0];
        return null;
    }

    /** Apps sorted by name, then the TV's inputs. Slow-ish (loads banners): call off the UI thread. */
    static List<Item> load(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, Item> found = new LinkedHashMap<>();
        collect(c, pm, found, new Intent(Intent.ACTION_MAIN).addCategory(LEANBACK), true);
        collect(c, pm, found, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), false);
        List<Item> apps = new ArrayList<>(found.values());
        Collections.sort(apps, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        apps.addAll(inputs(c));
        return apps;
    }

    private static void collect(Context c, PackageManager pm, Map<String, Item> found, Intent q, boolean tv) {
        List<ResolveInfo> list;
        try {
            list = pm.queryIntentActivities(q, 0);
        } catch (Exception e) {
            return;
        }
        for (ResolveInfo ri : list) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || c.getPackageName().equals(ai.packageName) || found.containsKey(ai.packageName)) continue;
            try {
                Item it = new Item();
                it.id = ai.packageName;
                it.kind = "app";
                it.pkg = ai.packageName;
                it.cls = ai.name;
                it.label = String.valueOf(ri.loadLabel(pm)).trim();
                it.tv = tv;
                it.brand = brandFor(ai.packageName);
                if (it.brand == null) {
                    if (Build.VERSION.SDK_INT >= 20) {
                        it.banner = ai.loadBanner(pm);
                        if (it.banner == null && ai.applicationInfo != null) it.banner = ai.applicationInfo.loadBanner(pm);
                    }
                    if (it.banner == null) it.icon = ri.loadIcon(pm);
                }
                found.put(it.id, it);
            } catch (Exception ignored) {
                // one broken app must not hide the rest
            }
        }
    }

    private static List<Item> inputs(Context c) {
        List<Item> out = new ArrayList<>();
        if (Build.VERSION.SDK_INT < 21) return out;
        try {
            TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) return out;
            for (TvInputInfo i : tim.getTvInputList()) {
                if (!i.isPassthroughInput() || i.getParentId() != null) continue;
                if (Build.VERSION.SDK_INT >= 23 && i.isHidden(c)) continue;
                CharSequence label = null;
                if (Build.VERSION.SDK_INT >= 23) label = i.loadCustomLabel(c);
                if (label == null || label.length() == 0) label = i.loadLabel(c);
                Item it = new Item();
                it.id = "in:" + i.getId();
                it.kind = "input";
                it.inputId = i.getId();
                it.label = String.valueOf(label).trim();
                out.add(it);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** The dock's apps: saved choice, or a sensible default (written back so Settings agrees). */
    static List<String> homeIds(Context c, List<Item> items) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Map<String, Item> byId = new LinkedHashMap<>();
        for (Item it : items) byId.put(it.id, it);
        List<String> home = new ArrayList<>();
        try {
            String raw = sp.getString("home", null);
            if (raw != null) {
                JSONArray a = new JSONArray(raw);
                for (int i = 0; i < a.length(); i++) if (byId.containsKey(a.getString(i))) home.add(a.getString(i));
            }
        } catch (Exception ignored) {
        }
        if (home.isEmpty()) {
            for (String b : new String[]{"youtube", "netflix", "spotify", "dstv", "prime"}) {
                for (Item it : items) {
                    if (b.equals(it.brand) && !home.contains(it.id)) { home.add(it.id); break; }
                }
            }
            Pattern skip = Pattern.compile("settings|launcher|vending|tvrecommendations", Pattern.CASE_INSENSITIVE);
            for (int pass = 0; pass < 2 && home.size() < MAX_HOME; pass++) {
                for (Item it : items) {
                    if (home.size() >= MAX_HOME) break;
                    if (!"app".equals(it.kind) || home.contains(it.id) || skip.matcher(it.pkg).find()) continue;
                    if (pass == 0 && !it.tv) continue;
                    home.add(it.id);
                }
            }
            if (!home.isEmpty()) sp.edit().putString("home", new JSONArray(home).toString()).apply();
        }
        while (home.size() > MAX_HOME) home.remove(home.size() - 1);
        return home;
    }

    static void launch(Context c, Item it) {
        if (it == null) return;
        if ("input".equals(it.kind)) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(it.inputId));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
            } catch (Exception e) {
                Toast.makeText(c, "This TV can't switch input from here", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        PackageManager pm = c.getPackageManager();
        Intent i = null;
        if (Build.VERSION.SDK_INT >= 21) i = pm.getLeanbackLaunchIntentForPackage(it.pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(it.pkg);
        if (i == null && it.cls != null) i = new Intent(Intent.ACTION_MAIN).setClassName(it.pkg, it.cls);
        if (i == null) {
            Toast.makeText(c, "Can't open this app", Toast.LENGTH_SHORT).show();
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            c.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(c, "Can't open this app", Toast.LENGTH_SHORT).show();
        }
    }
}
