package com.pijamaparty.store.settings.application;

import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetStoreSettingsService implements GetStoreSettings {

    private final LoadStoreSettings loadSettings;

    GetStoreSettingsService(LoadStoreSettings loadSettings) {
        this.loadSettings = loadSettings;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        return new Result.Found(loadSettings.load());
    }
}
