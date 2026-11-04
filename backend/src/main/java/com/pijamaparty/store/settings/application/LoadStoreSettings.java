package com.pijamaparty.store.settings.application;

import com.pijamaparty.store.settings.domain.StoreSettings;

/** Outbound port, read side of the store's settings (UC-36): the single row the migration created. */
public interface LoadStoreSettings {

    StoreSettings load();
}
