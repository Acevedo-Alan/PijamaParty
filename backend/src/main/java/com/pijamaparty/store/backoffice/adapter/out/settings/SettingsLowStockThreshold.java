package com.pijamaparty.store.backoffice.adapter.out.settings;

import com.pijamaparty.store.backoffice.application.LowStockThreshold;
import com.pijamaparty.store.settings.api.StoreSettings;
import org.springframework.stereotype.Component;

/** D-E8-03: the threshold of "Datos de la tienda" (UC-36), read through the settings module's public API. */
@Component
class SettingsLowStockThreshold implements LowStockThreshold {

    private final StoreSettings settings;

    SettingsLowStockThreshold(StoreSettings settings) {
        this.settings = settings;
    }

    @Override
    public int units() {
        return settings.lowStockThreshold();
    }
}
