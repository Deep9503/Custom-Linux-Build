package com.experiment.autobrowser;

import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** Phone UI: the same browser and settings, for testing without a car. */
public class MainActivity extends AppCompatActivity {

    private WebView web;
    private EditText address;
    private ProgressBar progress;
    private TextView blockedLabel;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int pad = dp(6);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);

        // Row 1: address / search box + Go
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(pad, pad, pad, 0);
        address = new EditText(this);
        address.setSingleLine(true);
        address.setHint("Search or type URL");
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setSelectAllOnFocus(true);
        address.setOnEditorActionListener((v, id, ev) -> {
            boolean enter = ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && ev.getAction() == KeyEvent.ACTION_DOWN;
            if (id == EditorInfo.IME_ACTION_GO || enter) { go(); return true; }
            return false;
        });
        Button goBtn = new Button(this);
        goBtn.setText("Go");
        goBtn.setOnClickListener(v -> go());
        bar.addView(address, new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(goBtn);

        // Row 2: search engine, desktop mode, nav buttons
        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setPadding(pad, 0, pad, 0);
        Spinner engine = new Spinner(this);
        String[] names = new String[Prefs.ENGINES.length];
        for (int i = 0; i < names.length; i++) names[i] = Prefs.ENGINES[i][0];
        ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        engine.setAdapter(ad);
        engine.setSelection(Prefs.engineIndex(this));
        engine.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                Prefs.setEngineIndex(MainActivity.this, pos);
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        CheckBox desktop = new CheckBox(this);
        desktop.setText("Desktop");
        desktop.setChecked(Prefs.desktopMode(this));
        desktop.setOnCheckedChangeListener((b, on) -> {
            Prefs.setDesktopMode(this, on);
            WebSetup.applyDesktopMode(this, web);
            web.reload();
        });
        Button back = small("◀", v -> { if (web.canGoBack()) web.goBack(); });
        Button fwd = small("▶", v -> { if (web.canGoForward()) web.goForward(); });
        Button reload = small("⟳", v -> web.reload());
        Button home = small("⌂", v -> web.loadUrl(Prefs.home(this)));
        Button setHome = small("★", v -> {
            Prefs.setHome(this, web.getUrl());
            Toast.makeText(this, "Home page saved", Toast.LENGTH_SHORT).show();
        });
        tools.addView(engine, new LinearLayout.LayoutParams(0, -2, 1f));
        tools.addView(desktop);
        tools.addView(back);
        tools.addView(fwd);
        tools.addView(reload);
        tools.addView(home);
        tools.addView(setHome);

        // Row 3: ad blocking + send to car
        LinearLayout row3 = new LinearLayout(this);
        row3.setGravity(Gravity.CENTER_VERTICAL);
        row3.setPadding(pad, 0, pad, 0);
        CheckBox adblock = new CheckBox(this);
        adblock.setText("Block ads");
        adblock.setChecked(Prefs.adBlock(this));
        adblock.setOnCheckedChangeListener((b, on) -> {
            Prefs.setAdBlock(this, on);
            web.reload();
        });
        blockedLabel = new TextView(this);
        blockedLabel.setPadding(pad, 0, pad, 0);
        Button update = small("Update filters", v -> {
            Toast.makeText(this, "Downloading ad filter lists…", Toast.LENGTH_SHORT).show();
            AdBlocker.update(this, msg -> {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                refreshBlocked();
            });
        });
        Button toCar = small("Send to car", v -> sendToCar());
        row3.addView(adblock);
        row3.addView(blockedLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        row3.addView(update);
        row3.addView(toCar);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);

        web = new WebView(this);
        AdBlocker.init(this);
        WebSetup.configure(this, web);
        web.setWebViewClient(new BrowserClient(this, new BrowserClient.Callbacks() {
            @Override
            public void onStarted(String url) {
                if (!address.hasFocus()) address.setText(url);
            }

            @Override
            public void onFinished(WebView v, String url) {
                refreshBlocked();
            }
        }, null));
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenView != null) { callback.onCustomViewHidden(); return; }
                fullscreenView = view;
                fullscreenCallback = callback;
                view.setBackgroundColor(android.graphics.Color.BLACK);
                ((android.view.ViewGroup) getWindow().getDecorView()).addView(view,
                        new android.widget.FrameLayout.LayoutParams(-1, -1));
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }

            @Override
            public void onProgressChanged(WebView v, int p) {
                progress.setProgress(p);
                progress.setVisibility(p >= 100 ? View.INVISIBLE : View.VISIBLE);
            }
        });

        root.addView(bar);
        root.addView(tools);
        root.addView(row3);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        if (state != null) web.restoreState(state);
        else web.loadUrl(Prefs.home(this));
    }

    private void go() {
        String text = address.getText().toString();
        if (!text.trim().isEmpty()) Prefs.addRecent(this, text.trim());
        web.loadUrl(Prefs.resolve(this, text));
        address.clearFocus();
        web.requestFocus();
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        imm.hideSoftInputFromWindow(address.getWindowToken(), 0);
    }

    @SuppressWarnings("deprecation")
    private void exitFullscreen() {
        if (fullscreenView == null) return;
        ((android.view.ViewGroup) getWindow().getDecorView()).removeView(fullscreenView);
        fullscreenView = null;
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        WebChromeClient.CustomViewCallback cb = fullscreenCallback;
        fullscreenCallback = null;
        if (cb != null) cb.onCustomViewHidden();
    }

    private void refreshBlocked() {
        blockedLabel.setText(Prefs.adBlock(this) ? "Blocked: " + AdBlocker.blockedCount() : "");
    }

    /** Use the phone keyboard to search, then show the result on the car screen. */
    private void sendToCar() {
        CarBrowser car = CarBrowser.active;
        if (car == null) {
            Toast.makeText(this, "Auto Browser isn't open in Android Auto", Toast.LENGTH_SHORT).show();
            return;
        }
        String text = address.getText().toString().trim();
        String url = text.isEmpty() || text.equals(web.getUrl()) ? web.getUrl() : Prefs.resolve(this, text);
        if (!text.isEmpty()) Prefs.addRecent(this, text);
        car.load(url);
        Toast.makeText(this, "Opened on car screen", Toast.LENGTH_SHORT).show();
    }

    private Button small(String label, View.OnClickListener l) {
        Button b = new Button(this, null, android.R.attr.buttonStyleSmall);
        b.setText(label);
        b.setMinWidth(dp(40));
        b.setMinimumWidth(dp(40));
        b.setOnClickListener(l);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (fullscreenView != null) exitFullscreen();
        else if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
