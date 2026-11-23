package com.experiment.autobrowser;

import java.io.BufferedReader;
import java.io.FileReader;

/** CI sanity check: parse the real filter lists and test a few known hosts. */
public class FilterCheck {
    public static void main(String[] args) throws Exception {
        long t0 = System.currentTimeMillis();
        FilterEngine e = new FilterEngine();
        for (String f : args) {
            try (BufferedReader r = new BufferedReader(new FileReader(f))) { e.addAll(r); }
        }
        e.finish();
        System.out.println("parse ms=" + (System.currentTimeMillis() - t0)
                + " network=" + e.networkRuleCount() + " cosmetic=" + e.cosmeticRuleCount());
        String[][] hosts = {
                {"securepubads.g.doubleclick.net", "www.cnn.com", "true"},
                {"pagead2.googlesyndication.com", "www.abc.net.au", "true"},
                {"www.google-analytics.com", "www.example.com", "true"},
                {"www.youtube.com", "www.youtube.com", "false"},
                {"i.ytimg.com", "www.youtube.com", "false"},
                {"rr1---sn-abc.googlevideo.com", "m.youtube.com", "false"},
                {"www.gstatic.com", "www.google.com", "false"},
                {"cdn.jsdelivr.net", "example.com", "false"},
        };
        int bad = 0;
        for (String[] h : hosts) {
            boolean got = e.shouldBlock(h[0], h[1]);
            boolean ok = got == Boolean.parseBoolean(h[2]);
            if (!ok) bad++;
            System.out.println((ok ? "OK   " : "WARN ") + h[0] + " from " + h[1] + " -> blocked=" + got);
        }
        for (String h : new String[]{"www.cnn.com", "m.youtube.com", "www.google.com"}) {
            long t = System.currentTimeMillis();
            System.out.println("css " + h + " = " + e.css(h).length() + " chars ("
                    + (System.currentTimeMillis() - t) + " ms)");
        }
        System.out.println(bad == 0 ? "Filter check passed" : bad + " unexpected results (non-fatal)");
    }
}
