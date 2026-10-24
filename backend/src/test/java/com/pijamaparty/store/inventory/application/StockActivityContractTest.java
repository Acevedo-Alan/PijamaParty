package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.activity.testing.ActivityPortContractTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.List;
import java.util.UUID;

/** Contract of {@link StockActivity}: every entry recorded is read back whole. */
public abstract class StockActivityContractTest<T extends StockActivity> extends ActivityPortContractTest<T> {

    @Override
    protected void record(ActivityEntry entry) {
        subject.record(entry);
    }

    @Override
    protected ActivityEntry entryWithChanges() {
        return new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.STOCK_ADJUSTED,
            ActivitySubject.of(SubjectType.VARIANT, UUID.randomUUID(), "PP-AB12CD34"),
            List.of(FieldChange.of("quantity", null, "+5"), FieldChange.of("stock", "3", "8")));
    }

    @Override
    protected ActivityEntry entryWithoutChanges() {
        return new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.STOCK_ADJUSTED,
            ActivitySubject.of(SubjectType.VARIANT, UUID.randomUUID()));
    }
}
