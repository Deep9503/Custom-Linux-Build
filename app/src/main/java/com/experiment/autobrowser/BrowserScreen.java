package com.experiment.autobrowser;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.OnClickListener;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

/** Main car screen: the web page fills the display, with browser buttons on the side. */
public class BrowserScreen extends Screen {

    private final CarBrowser browser;

    public BrowserScreen(@NonNull CarContext ctx, CarBrowser browser) {
        super(ctx);
        this.browser = browser;
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ActionStrip actions = new ActionStrip.Builder()
                .addAction(icon(R.drawable.ic_search, browser::showAddressKeyboard))
                .addAction(icon(R.drawable.ic_back, browser::back))
                .addAction(icon(R.drawable.ic_refresh, browser::reload))
                .addAction(icon(R.drawable.ic_menu, () ->
                        getScreenManager().push(new MenuScreen(getCarContext(), browser))))
                .build();

        // PAN enables drag-to-scroll on the page; up/down page through it.
        ActionStrip mapActions = new ActionStrip.Builder()
                .addAction(Action.PAN)
                .addAction(icon(R.drawable.ic_up, browser::pageUp))
                .addAction(icon(R.drawable.ic_down, browser::pageDown))
                .addAction(icon(R.drawable.ic_tabs, () ->
                        getScreenManager().push(new TabsScreen(getCarContext(), browser))))
                .build();

        return new NavigationTemplate.Builder()
                .setActionStrip(actions)
                .setMapActionStrip(mapActions)
                .setPanModeListener(isInPanMode -> { })
                .build();
    }

    /**
     * Icons are sent as pictures rather than resource IDs: Android Auto caches icons by
     * resource ID, so after an app update the car could show old icons on the wrong buttons.
     */
    private Bitmap bitmap(@DrawableRes int res) {
        Drawable d = ContextCompat.getDrawable(getCarContext(), res);
        Bitmap b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        if (d != null) {
            d.setBounds(0, 0, 96, 96);
            d.draw(new Canvas(b));
        }
        return b;
    }

    private Action icon(@DrawableRes int res, OnClickListener onClick) {
        return new Action.Builder()
                .setIcon(new CarIcon.Builder(IconCompat.createWithBitmap(bitmap(res))).build())
                .setOnClickListener(onClick)
                .build();
    }
}
