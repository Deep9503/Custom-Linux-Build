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
import androidx.car.app.model.Toggle;

/** Extra browser actions and settings. */
public class MenuScreen extends Screen {

    private final CarBrowser browser;

    public MenuScreen(@NonNull CarContext ctx, CarBrowser browser) {
        super(ctx);
        this.browser = browser;
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        CarContext c = getCarContext();
        String title = Prefs.adBlock(c)
                ? "Ads & trackers blocked: " + AdBlocker.blockedCount()
                : (browser.title().isEmpty() ? "Browser" : browser.title());

        ItemList list = new ItemList.Builder()
                .addItem(new Row.Builder()
                        .setTitle("Search engine")
                        .addText(Prefs.engineName(c))
                        .setBrowsable(true)
                        .setOnClickListener(() ->
                                getScreenManager().push(new EngineScreen(c)))
                        .build())
                .addItem(new Row.Builder()
                        .setTitle("Ad & tracker blocking")
                        .addText(AdBlocker.summary() + " + YouTube ads")
                        .setToggle(new Toggle.Builder(on -> {
                            Prefs.setAdBlock(c, on);
                            browser.reload();
                            invalidate();
                        }).setChecked(Prefs.adBlock(c)).build())
                        .build())
                .addItem(new Row.Builder()
                        .setTitle("Recent & quick links")
                        .setBrowsable(true)
                        .setOnClickListener(() -> getScreenManager().push(
                                new InputScreen(c, browser, InputScreen.MODE_URL)))
                        .build())
                .addItem(new Row.Builder()
                        .setTitle("Forward")
                        .setOnClickListener(() -> { browser.forward(); getScreenManager().pop(); })
                        .build())
                .addItem(new Row.Builder()
                        .setTitle("Home")
                        .addText(Prefs.home(c))
                        .setOnClickListener(() -> {
                            browser.load(Prefs.home(c));
                            getScreenManager().pop();
                        })
                        .build())
                .addItem(new Row.Builder()
                        .setTitle("Desktop site")
                        .setToggle(new Toggle.Builder(on -> {
                            Prefs.setDesktopMode(c, on);
                            browser.applyDesktopMode();
                        }).setChecked(Prefs.desktopMode(c)).build())
                        .build())
                .addItem(new Row.Builder()
                        .setTitle("Set this page as home")
                        .addText(browser.url() == null ? "(no page loaded)" : browser.url())
                        .setOnClickListener(() -> {
                            Prefs.setHome(c, browser.url());
                            CarToast.makeText(c, "Home page saved", CarToast.LENGTH_SHORT).show();
                            invalidate();
                        })
                        .build())
                .build();

        return new ListTemplate.Builder()
                .setTitle(title)
                .setHeaderAction(Action.BACK)
                .setSingleList(list)
                .build();
    }
}
