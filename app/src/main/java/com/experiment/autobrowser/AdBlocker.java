package com.experiment.autobrowser;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ad and tracker blocking using the same public filter lists Brave's shields are built on
 * (EasyList, EasyPrivacy, Peter Lowe's list). Lists bundled in the APK are used at first,
 * then refreshed from the internet weekly (or on demand from the phone app).
 */
public final class AdBlocker {

    private static final String TAG = "AutoBrowser";
    private static final long WEEK_MS = 7L * 24 * 60 * 60 * 1000;

    static final String[][] LISTS = {
            {"easylist.txt", "https://easylist.to/easylist/easylist.txt"},
            {"easyprivacy.txt", "https://easylist.to/easylist/easyprivacy.txt"},
            {"peterlowe.txt", "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=adblockplus&showintro=0&mimetype=plaintext"},
    };

    private static volatile FilterEngine engine = FilterEngine.EMPTY;
    private static final AtomicInteger blocked = new AtomicInteger();
    private static boolean started;
    private static volatile boolean busy;

    private AdBlocker() {}

    /** Load filters in the background (once per process) and refresh them if a week old. */
    public static synchronized void init(Context ctx) {
        if (started) return;
        started = true;
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            engine = load(app);
            if (System.currentTimeMillis() - Prefs.filtersUpdated(app) > WEEK_MS) download(app);
        }, "adblock-init").start();
    }

    /** Download fresh lists now. {@code done} runs on the main thread with a status message. */
    public static void update(Context ctx, Callback done) {
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            boolean ok = download(app);
            String msg = ok ? "Ad filters updated: " + engine.networkRuleCount() + " blocked domains, "
                    + engine.cosmeticRuleCount() + " hiding rules"
                    : "Could not download ad filters (using current lists)";
            if (done != null) new Handler(Looper.getMainLooper()).post(() -> done.onDone(msg));
        }, "adblock-update").start();
    }

    public interface Callback { void onDone(String message); }

    public static boolean shouldBlock(String host, String pageHost) {
        boolean b = engine.shouldBlock(host, pageHost);
        if (b) blocked.incrementAndGet();
        return b;
    }

    public static String css(String host) { return engine.css(host); }

    public static int blockedCount() { return blocked.get(); }

    public static String summary() {
        return engine.networkRuleCount() + " domains, " + engine.cosmeticRuleCount() + " hiding rules";
    }

    // ------------------------------------------------------------------ internals

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "filters");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private static FilterEngine load(Context c) {
        FilterEngine e = new FilterEngine();
        for (String[] l : LISTS) {
            File f = new File(dir(c), l[0]);
            try (InputStream in = f.exists() ? new FileInputStream(f) : c.getAssets().open("filters/" + l[0]);
                 BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                e.addAll(r);
            } catch (Exception ex) {
                Log.w(TAG, "Filter list unavailable: " + l[0]);
            }
        }
        e.finish();
        Log.i(TAG, "Ad blocker loaded: " + e.networkRuleCount() + " network, "
                + e.cosmeticRuleCount() + " cosmetic rules");
        return e;
    }

    private static boolean download(Context c) {
        if (busy) return false;
        busy = true;
        boolean any = false;
        try {
            for (String[] l : LISTS) {
                File tmp = new File(dir(c), l[0] + ".tmp");
                try {
                    HttpURLConnection con = (HttpURLConnection) new URL(l[1]).openConnection();
                    con.setConnectTimeout(15000);
                    con.setReadTimeout(30000);
                    con.setRequestProperty("User-Agent", "AutoBrowser/0.2");
                    if (con.getResponseCode() != 200) throw new Exception("HTTP " + con.getResponseCode());
                    try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                    if (tmp.length() > 1000 && tmp.renameTo(new File(dir(c), l[0]))) any = true;
                } catch (Exception ex) {
                    Log.w(TAG, "Download failed: " + l[1] + " " + ex);
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                }
            }
            if (any) {
                engine = load(c);
                Prefs.setFiltersUpdated(c, System.currentTimeMillis());
            }
        } finally {
            busy = false;
        }
        return any;
    }
}
