package com.pijamaparty.store.catalog.adapter.out.settings;

import com.pijamaparty.store.catalog.application.LowStockThreshold;
import com.pijamaparty.store.settings.api.StoreSettings;
import org.springframework.stereotype.Component;

/** D-E8-03: the threshold of "Datos de la tienda" (UC-36), read through the settings module's public API. */
@Component
class StoreSettingsLowStockThreshold implements LowStockThreshold {

    private final StoreSettings settings;

    StoreSettingsLowStockThreshold(StoreSettings settings) {
        this.settings = settings;
    }

    @Override
    public int units() {
        return settings.lowStockThreshold();
    }
}
