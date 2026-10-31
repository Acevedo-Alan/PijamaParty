package com.pijamaparty.store.backoffice.adapter.out.settings;

import com.pijamaparty.store.backoffice.application.LowStockThreshold;
import org.springframework.stereotype.Component;

/**
 * D-E8-03: 3 units, the default of the store's settings. The summary came before the settings (UC-33 design notes):
 * T-E8-04 replaces this adapter with one that reads {@code settings.api.StoreSettings}.
 */
@Component
class DefaultLowStockThreshold implements LowStockThreshold {

    static final int DEFAULT_UNITS = 3;

    @Override
    public int units() {
        return DEFAULT_UNITS;
    }
}
