package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.activity.testing.ActivityPortContractTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.List;
import java.util.UUID;

/** Contract of {@link CatalogActivity}: every entry recorded is read back whole. */
public abstract class CatalogActivityContractTest<T extends CatalogActivity> extends ActivityPortContractTest<T> {

    @Override
    protected void record(ActivityEntry entry) {
        subject.record(entry);
    }

    @Override
    protected ActivityEntry entryWithChanges() {
        return new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.VARIANT_UPDATED,
            ActivitySubject.of(SubjectType.VARIANT, UUID.randomUUID(), "PP-AB12CD34"),
            List.of(FieldChange.of("price", "$20.000", "$22.000"), FieldChange.of("active", "true", "false")));
    }

    @Override
    protected ActivityEntry entryWithoutChanges() {
        return new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.CATEGORY_DELETED,
            ActivitySubject.of(SubjectType.CATEGORY, UUID.randomUUID(), "Verano"));
    }
}
