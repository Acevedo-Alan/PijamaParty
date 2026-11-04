package com.pijamaparty.store.settings.application;

import com.pijamaparty.store.activity.api.ActivityEntry;
import java.util.List;

class InMemorySettingsActivityContractTest extends SettingsActivityContractTest<InMemorySettingsActivity> {

    @Override
    protected InMemorySettingsActivity createSubject() {
        return new InMemorySettingsActivity();
    }

    @Override
    protected List<ActivityEntry> recorded() {
        return subject.entries();
    }
}
