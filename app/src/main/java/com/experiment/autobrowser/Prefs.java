package com.experiment.autobrowser;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Settings shared by the phone UI and the Android Auto UI. */
public final class Prefs {

    /** Search engines: display name + query URL prefix. */
    public static final String[][] ENGINES = {
            {"Google", "https://www.google.com/search?q="},
            {"DuckDuckGo", "https://duckduckgo.com/?q="},
            {"Bing", "https://www.bing.com/search?q="},
            {"Brave Search", "https://search.brave.com/search?q="},
            {"YouTube", "https://www.youtube.com/results?search_query="},
            {"Wikipedia", "https://en.wikipedia.org/w/index.php?search="},
    };

    public static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final String FILE = "auto_browser";
    private static final String K_ENGINE = "engine";
    private static final String K_HOME = "home";
    private static final String K_DESKTOP = "desktop";
    private static final String K_RECENT = "recent";
    private static final String K_ADBLOCK = "adblock";
    private static final String K_FILTERS_UPDATED = "filters_updated";
    private static final String SEP = "\u001F";
    private static final int MAX_RECENT = 5;

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static int engineIndex(Context c) {
        int i = sp(c).getInt(K_ENGINE, 0);
        return (i < 0 || i >= ENGINES.length) ? 0 : i;
    }

    public static void setEngineIndex(Context c, int i) {
        sp(c).edit().putInt(K_ENGINE, i).apply();
    }

    public static String engineName(Context c) {
        return ENGINES[engineIndex(c)][0];
    }

    public static String home(Context c) {
        return sp(c).getString(K_HOME, "https://www.google.com");
    }

    public static void setHome(Context c, String url) {
        sp(c).edit().putString(K_HOME, url).apply();
    }

    public static boolean desktopMode(Context c) {
        return sp(c).getBoolean(K_DESKTOP, false);
    }

    public static void setDesktopMode(Context c, boolean on) {
        sp(c).edit().putBoolean(K_DESKTOP, on).apply();
    }

    public static boolean adBlock(Context c) {
        return sp(c).getBoolean(K_ADBLOCK, true);
    }

    public static void setAdBlock(Context c, boolean on) {
        sp(c).edit().putBoolean(K_ADBLOCK, on).apply();
    }

    public static long filtersUpdated(Context c) {
        return sp(c).getLong(K_FILTERS_UPDATED, 0);
    }

    public static void setFiltersUpdated(Context c, long t) {
        sp(c).edit().putLong(K_FILTERS_UPDATED, t).apply();
    }

    public static List<String> recent(Context c) {
        String raw = sp(c).getString(K_RECENT, "");
        List<String> out = new ArrayList<>();
        if (!raw.isEmpty()) out.addAll(Arrays.asList(raw.split(SEP)));
        return out;
    }

    public static void addRecent(Context c, String entry) {
        List<String> list = recent(c);
        list.remove(entry);
        list.add(0, entry);
        while (list.size() > MAX_RECENT) list.remove(list.size() - 1);
        sp(c).edit().putString(K_RECENT, String.join(SEP, list)).apply();
    }

    /** Turns what the user typed into a URL: a web address is opened, anything else is searched. */
    public static String resolve(Context c, String input) {
        String t = input == null ? "" : input.trim();
        if (t.isEmpty()) return home(c);
        String lower = t.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("about:") || lower.startsWith("file:")) {
            return t;
        }
        boolean looksLikeHost = !t.contains(" ")
                && (t.matches("^[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/.*)?$") || lower.startsWith("localhost"));
        if (looksLikeHost) return "https://" + t;
        return ENGINES[engineIndex(c)][1] + Uri.encode(t);
    }

    /** Search with a specific engine, regardless of the default. */
    public static String searchWith(int engine, String query) {
        return ENGINES[engine][1] + Uri.encode(query.trim());
    }
}
