package com.pijamaparty.store.settings.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.activity.testing.ActivityPortContractTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.List;

/** Contract of {@link SettingsActivity}: every entry recorded is read back whole. */
public abstract class SettingsActivityContractTest<T extends SettingsActivity> extends ActivityPortContractTest<T> {

    @Override
    protected void record(ActivityEntry entry) {
        subject.record(entry);
    }

    @Override
    protected ActivityEntry entryWithChanges() {
        return new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.STORE_SETTINGS_CHANGED,
            ActivitySubject.of(SubjectType.STORE_SETTINGS),
            List.of(FieldChange.of("announcement", null, "Envío gratis a todo Santa Fe"),
                FieldChange.of("lowStockThreshold", "3", "5")));
    }

    @Override
    protected ActivityEntry entryWithoutChanges() {
        return new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.STORE_SETTINGS_CHANGED,
            ActivitySubject.of(SubjectType.STORE_SETTINGS));
    }
}
