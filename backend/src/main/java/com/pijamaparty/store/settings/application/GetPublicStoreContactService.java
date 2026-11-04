package com.pijamaparty.store.settings.application;

import com.pijamaparty.store.settings.domain.StoreSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetPublicStoreContactService implements GetPublicStoreContact {

    private final LoadStoreSettings loadSettings;

    GetPublicStoreContactService(LoadStoreSettings loadSettings) {
        this.loadSettings = loadSettings;
    }

    @Override
    @Transactional(readOnly = true)
    public StoreSettings execute() {
        return loadSettings.load();
    }
}
