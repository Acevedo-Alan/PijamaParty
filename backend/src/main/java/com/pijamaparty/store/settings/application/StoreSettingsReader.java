package com.pijamaparty.store.settings.application;

import com.pijamaparty.store.settings.api.StoreSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link StoreSettings} for the other modules (D-E8-03): the threshold of the summary and the Stock screen. */
@Service
class StoreSettingsReader implements StoreSettings {

    private final LoadStoreSettings loadSettings;

    StoreSettingsReader(LoadStoreSettings loadSettings) {
        this.loadSettings = loadSettings;
    }

    @Override
    @Transactional(readOnly = true)
    public int lowStockThreshold() {
        return loadSettings.load().lowStockThreshold().units();
    }
}
