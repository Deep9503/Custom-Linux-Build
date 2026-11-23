package com.experiment.autobrowser;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.car.app.AppManager;
import androidx.car.app.CarAppService;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

public class BrowserCarAppService extends CarAppService {

    @NonNull
    @Override
    public HostValidator createHostValidator() {
        // Experimental build: accept any host (Android Auto, the Desktop Head Unit, etc.).
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @NonNull
    @Override
    public Session onCreateSession() {
        return new Session() {
            private CarBrowser browser;

            @NonNull
            @Override
            public Screen onCreateScreen(@NonNull Intent intent) {
                browser = new CarBrowser(getCarContext());
                getCarContext().getCarService(AppManager.class).setSurfaceCallback(browser);
                getLifecycle().addObserver(new DefaultLifecycleObserver() {
                    @Override
                    public void onDestroy(@NonNull LifecycleOwner owner) {
                        browser.release();
                    }
                });
                return new BrowserScreen(getCarContext(), browser);
            }
        };
    }
}
