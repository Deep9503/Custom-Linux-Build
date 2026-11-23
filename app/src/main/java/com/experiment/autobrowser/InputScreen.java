package com.experiment.autobrowser;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.Row;
import androidx.car.app.model.SearchTemplate;
import androidx.car.app.model.Template;

/**
 * Keyboard entry on the car screen.
 * MODE_URL: type a web address or search terms (searched with the chosen engine).
 * MODE_PAGE: type text into the focused field of the current page (e.g. a site's own search box).
 */
public class InputScreen extends Screen {

    public static final int MODE_URL = 0;
    public static final int MODE_PAGE = 1;

    private final CarBrowser browser;
    private final int mode;

    public InputScreen(@NonNull CarContext ctx, CarBrowser browser, int mode) {
        super(ctx);
        this.browser = browser;
        this.mode = mode;
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        SearchTemplate.Builder b = new SearchTemplate.Builder(new SearchTemplate.SearchCallback() {
            @Override
            public void onSearchTextChanged(@NonNull String text) { }

            @Override
            public void onSearchSubmitted(@NonNull String text) {
                submit(text);
            }
        })
                .setHeaderAction(Action.BACK)
                .setShowKeyboardByDefault(true);

        if (mode == MODE_PAGE) {
            b.setSearchHint("Type into the page's field");
            b.setItemList(new ItemList.Builder()
                    .setNoItemsMessage("Tap a text box on the page first, then type here.")
                    .build());
        } else {
            b.setSearchHint("Search " + Prefs.engineName(getCarContext()) + " or type a URL");
            b.setItemList(suggestions());
        }
        return b.build();
    }

    /** Recent entries + quick links, usable even when the keyboard is locked while driving. */
    private ItemList suggestions() {
        ItemList.Builder list = new ItemList.Builder();
        int count = 0;
        for (String r : Prefs.recent(getCarContext())) {
            list.addItem(row(r, "Recent", () -> submit(r)));
            count++;
        }
        String[][] quick = {
                {"Home page", Prefs.home(getCarContext())},
                {"Google", "https://www.google.com"},
                {"YouTube", "https://m.youtube.com"},
                {"Wikipedia", "https://en.m.wikipedia.org"},
        };
        for (String[] q : quick) {
            if (count++ >= 6) break;
            list.addItem(row(q[0], q[1], () -> open(q[1])));
        }
        return list.build();
    }

    private Row row(String title, String text, Runnable onClick) {
        return new Row.Builder()
                .setTitle(title)
                .addText(text)
                .setOnClickListener(onClick::run)
                .build();
    }

    private void submit(String text) {
        if (text.trim().isEmpty()) return;
        if (mode == MODE_PAGE) {
            browser.typeIntoPage(text, true);
            CarToast.makeText(getCarContext(), "Typed into page", CarToast.LENGTH_SHORT).show();
            getScreenManager().popToRoot();
            return;
        }
        Prefs.addRecent(getCarContext(), text.trim());
        open(Prefs.resolve(getCarContext(), text));
    }

    private void open(String url) {
        browser.load(url);
        getScreenManager().popToRoot();
    }
}
