package com.experiment.autobrowser;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A small Adblock Plus–syntax filter engine (the syntax used by EasyList, EasyPrivacy
 * and the lists Brave ships). Supports:
 *  - domain blocking rules:        ||ads.example.com^   (+ $third-party)
 *  - domain exception rules:       @@||example.com^     (+ $generichide / $elemhide)
 *  - generic element hiding:       ##.ad-banner
 *  - site-specific element hiding: example.com##.sidebar-ad
 *  - element hiding exceptions:    example.com#@#.ad-banner
 * Path/regex network rules, scriptlets and extended CSS are skipped.
 * No Android dependencies, so it can be unit tested on a desktop JVM.
 */
final class FilterEngine {

    private static final String[] SKIP_OPTIONS = {
            "domain=", "from=", "to=", "denyallow=", "popup", "csp", "redirect", "removeparam",
            "badfilter", "header", "permissions", "urltransform", "replace", "rewrite",
            "~third-party", "first-party", "1p", "strict1p", "strict3p", "method=",
    };
    private static final String[] EXTENDED_CSS = {
            ":-abp-", ":has-text(", ":upward(", ":xpath(", ":style(", ":remove(", ":matches-",
            ":min-text-length(", ":watch-attr(", ":others(", ":if(", ":if-not(", ":nth-ancestor(",
            ":contains(", ":-ext-", "[-ext-",
    };
    private static final Set<String> SECOND_LEVEL = new HashSet<>(Arrays.asList(
            "co", "com", "net", "org", "gov", "edu", "ac", "or", "ne", "go", "id", "asn"));
    private static final int CHUNK = 25;

    private final Set<String> block = new HashSet<>();
    private final Set<String> block3p = new HashSet<>();
    private final Set<String> allow = new HashSet<>();
    private final Set<String> noGenericHide = new HashSet<>();
    private final Set<String> noElemHide = new HashSet<>();
    private final Set<String> generic = new LinkedHashSet<>();
    private final Set<String> genericExcept = new HashSet<>();
    private final Map<String, List<String>> specific = new HashMap<>();
    private final Map<String, Set<String>> specificExcept = new HashMap<>();

    private String genericCss = "";
    private final Map<String, String> cssCache = new LinkedHashMap<String, String>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> e) { return size() > 48; }
    };

    static final FilterEngine EMPTY = new FilterEngine().finish();

    // ------------------------------------------------------------------ parsing

    void addAll(BufferedReader r) throws IOException {
        String line;
        while ((line = r.readLine()) != null) addLine(line);
    }

    void addLine(String raw) {
        String line = raw.trim();
        if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return;
        if (line.contains("#?#") || line.contains("#$#") || line.contains("#%#")
                || line.contains("#@?#") || line.contains("#@$#") || line.contains("#@%#")) return;

        int ex = line.indexOf("#@#");
        if (ex >= 0) { addCosmetic(line.substring(0, ex), line.substring(ex + 3), true); return; }
        int hide = line.indexOf("##");
        if (hide >= 0) { addCosmetic(line.substring(0, hide), line.substring(hide + 2), false); return; }
        addNetwork(line);
    }

    private void addNetwork(String line) {
        boolean exception = line.startsWith("@@");
        if (exception) line = line.substring(2);
        if (!line.startsWith("||")) return; // only domain-anchored rules

        String opts = "";
        int d = line.lastIndexOf('$');
        if (d >= 0) { opts = line.substring(d + 1).toLowerCase(); line = line.substring(0, d); }

        String host = line.substring(2);
        if (host.endsWith("^|")) host = host.substring(0, host.length() - 2);
        else if (host.endsWith("^")) host = host.substring(0, host.length() - 1);
        host = host.toLowerCase();
        if (!host.matches("[a-z0-9.-]+") || !host.contains(".")) return; // has a path or wildcard

        boolean thirdParty = false;
        if (!opts.isEmpty()) {
            for (String o : opts.split(",")) {
                if (exception && (o.equals("generichide") || o.equals("ghide"))) {
                    noGenericHide.add(host); return;
                }
                if (exception && (o.equals("elemhide") || o.equals("ehide"))) {
                    noElemHide.add(host); return;
                }
                for (String s : SKIP_OPTIONS) if (o.equals(s) || (s.endsWith("=") && o.startsWith(s))) return;
                if (o.equals("document") || o.equals("doc") || o.equals("specifichide")
                        || o.equals("shide") || o.equals("generichide") || o.equals("elemhide")) return;
                if (o.equals("third-party") || o.equals("3p")) thirdParty = true;
            }
        }
        if (exception) allow.add(host);
        else (thirdParty ? block3p : block).add(host);
    }

    private void addCosmetic(String domains, String selector, boolean exception) {
        selector = selector.trim();
        if (selector.isEmpty() || selector.startsWith("+js") || selector.startsWith("^")
                || selector.contains("{") || selector.contains("}")) return;
        for (String x : EXTENDED_CSS) if (selector.contains(x)) return;

        List<String> pos = new ArrayList<>();
        List<String> neg = new ArrayList<>();
        if (!domains.isEmpty()) {
            for (String dom : domains.split(",")) {
                dom = dom.trim().toLowerCase();
                if (dom.isEmpty() || dom.contains("*") || dom.contains("/")) continue;
                if (dom.startsWith("~")) neg.add(dom.substring(1)); else pos.add(dom);
            }
            if (pos.isEmpty() && neg.isEmpty()) return;
        }

        if (exception) {
            if (pos.isEmpty()) genericExcept.add(selector);
            for (String p : pos) specificExcept.computeIfAbsent(p, k -> new HashSet<>()).add(selector);
            return;
        }
        if (pos.isEmpty()) {
            generic.add(selector);
            for (String n : neg) specificExcept.computeIfAbsent(n, k -> new HashSet<>()).add(selector);
        } else {
            for (String p : pos) specific.computeIfAbsent(p, k -> new ArrayList<>()).add(selector);
        }
    }

    FilterEngine finish() {
        generic.removeAll(genericExcept);
        genericCss = toCss(generic, null);
        return this;
    }

    // ------------------------------------------------------------------ matching

    /** True if a request to {@code host}, made from a page on {@code pageHost}, should be blocked. */
    boolean shouldBlock(String host, String pageHost) {
        if (host == null || host.isEmpty()) return false;
        host = host.toLowerCase();
        for (String h = host; h != null; h = parent(h)) if (allow.contains(h)) return false;
        if (pageHost != null) {
            for (String h = pageHost.toLowerCase(); h != null; h = parent(h)) if (allow.contains(h)) return false;
        }
        boolean thirdParty = pageHost == null || pageHost.isEmpty()
                || !base(host).equals(base(pageHost.toLowerCase()));
        for (String h = host; h != null; h = parent(h)) {
            if (block.contains(h)) return true;
            if (thirdParty && block3p.contains(h)) return true;
        }
        return false;
    }

    /** CSS that hides ad elements on a page from {@code host}. */
    synchronized String css(String host) {
        host = host == null ? "" : host.toLowerCase();
        String cached = cssCache.get(host);
        if (cached != null) return cached;

        boolean elemHide = true, genericHide = true;
        Set<String> except = new HashSet<>();
        List<String> own = new ArrayList<>();
        for (String h = host; h != null && !h.isEmpty(); h = parent(h)) {
            if (noElemHide.contains(h)) elemHide = false;
            if (noGenericHide.contains(h)) genericHide = false;
            Set<String> e = specificExcept.get(h);
            if (e != null) except.addAll(e);
            List<String> s = specific.get(h);
            if (s != null) own.addAll(s);
        }
        String out;
        if (!elemHide) {
            out = "";
        } else {
            own.removeAll(except);
            StringBuilder sb = new StringBuilder();
            if (genericHide) sb.append(except.isEmpty() ? genericCss : toCss(generic, except));
            sb.append(toCss(own, null));
            out = sb.toString();
        }
        cssCache.put(host, out);
        return out;
    }

    private static String toCss(Iterable<String> selectors, Set<String> skip) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String s : selectors) {
            if (skip != null && skip.contains(s)) continue;
            if (n > 0) sb.append(',');
            sb.append(s);
            if (++n == CHUNK) { sb.append("{display:none!important}\n"); n = 0; }
        }
        if (n > 0) sb.append("{display:none!important}\n");
        return sb.toString();
    }

    static String parent(String h) {
        int i = h.indexOf('.');
        return (i < 0 || i == h.length() - 1) ? null : h.substring(i + 1);
    }

    /** Approximate registrable domain (handles e.g. example.com.au, example.co.uk). */
    static String base(String h) {
        String[] p = h.split("\\.");
        int n = p.length;
        if (n <= 2) return h;
        int keep = (p[n - 1].length() == 2 && SECOND_LEVEL.contains(p[n - 2])) ? 3 : 2;
        StringBuilder sb = new StringBuilder();
        for (int i = n - keep; i < n; i++) { if (sb.length() > 0) sb.append('.'); sb.append(p[i]); }
        return sb.toString();
    }

    int networkRuleCount() { return block.size() + block3p.size(); }
    int cosmeticRuleCount() {
        int c = generic.size();
        for (List<String> l : specific.values()) c += l.size();
        return c;
    }
}
