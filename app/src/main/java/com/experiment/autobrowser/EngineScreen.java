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

/** Pick the default search engine. */
public class EngineScreen extends Screen {

    public EngineScreen(@NonNull CarContext ctx) {
        super(ctx);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ItemList.Builder list = new ItemList.Builder()
                .setOnSelectedListener(index -> {
                    Prefs.setEngineIndex(getCarContext(), index);
                    CarToast.makeText(getCarContext(),
                            "Searching with " + Prefs.ENGINES[index][0], CarToast.LENGTH_SHORT).show();
                    getScreenManager().pop();
                })
                .setSelectedIndex(Prefs.engineIndex(getCarContext()));
        for (String[] e : Prefs.ENGINES) {
            list.addItem(new Row.Builder().setTitle(e[0]).build());
        }
        return new ListTemplate.Builder()
                .setTitle("Search engine")
                .setHeaderAction(Action.BACK)
                .setSingleList(list.build())
                .build();
    }
}
