package com.experiment.autobrowser;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

import java.util.List;

/** Tab switcher: open a new tab, switch tabs, close the current one. */
public class TabsScreen extends Screen {

    private final CarBrowser browser;

    public TabsScreen(@NonNull CarContext ctx, CarBrowser browser) {
        super(ctx);
        this.browser = browser;
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        List<CarBrowser.Tab> tabs = browser.tabs();
        int cur = browser.currentIndex();
        ItemList.Builder list = new ItemList.Builder();

        list.addItem(new Row.Builder()
                .setTitle("+ New tab")
                .addText(tabs.size() >= CarBrowser.MAX_TABS ? "Tab limit reached (" + CarBrowser.MAX_TABS + ")"
                        : "Opens your home page with the keyboard")
                .setOnClickListener(() -> {
                    if (browser.newTab(Prefs.home(getCarContext()))) {
                        getScreenManager().popToRoot();
                        browser.showAddressKeyboard();
                    } else {
                        CarToast.makeText(getCarContext(), "Close a tab first", CarToast.LENGTH_SHORT).show();
                    }
                })
                .build());

        list.addItem(new Row.Builder()
                .setTitle("✕ Close current tab")
                .addText(shorten(tabs.get(cur).title()))
                .setOnClickListener(() -> {
                    browser.closeTab(browser.currentIndex());
                    if (browser.tabs().size() <= 1) getScreenManager().pop(); else invalidate();
                })
                .build());

        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;
            CarBrowser.Tab t = tabs.get(i);
            list.addItem(new Row.Builder()
                    .setTitle((i == cur ? "● " : "") + shorten(t.title()))
                    .addText(shorten(t.url()))
                    .setOnClickListener(() -> {
                        browser.switchTo(index);
                        getScreenManager().pop();
                    })
                    .build());
        }

        return new ListTemplate.Builder()
                .setTitle("Tabs (" + tabs.size() + ")")
                .setHeaderAction(Action.BACK)
                .setSingleList(list.build())
                .build();
    }

    private static String shorten(String s) {
        if (s == null || s.isEmpty()) return "New tab";
        return s.length() > 60 ? s.substring(0, 57) + "…" : s;
    }
}
