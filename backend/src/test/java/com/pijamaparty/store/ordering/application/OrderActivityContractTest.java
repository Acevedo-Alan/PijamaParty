package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.activity.testing.ActivityPortContractTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.List;
import java.util.UUID;

/** Contract of {@link OrderActivity}: every entry recorded is read back whole. */
public abstract class OrderActivityContractTest<T extends OrderActivity> extends ActivityPortContractTest<T> {

    @Override
    protected void record(ActivityEntry entry) {
        subject.record(entry);
    }

    @Override
    protected ActivityEntry entryWithChanges() {
        return new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.ORDER_STATUS_CHANGED,
            ActivitySubject.of(SubjectType.ORDER, UUID.randomUUID(), "#1001"),
            List.of(FieldChange.of("status", "PREPARING", "SHIPPED"), FieldChange.of("trackingNumber", null,
                "AR123456789")));
    }

    @Override
    protected ActivityEntry entryWithoutChanges() {
        return new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.WITHDRAWAL_RESOLVED,
            ActivitySubject.of(SubjectType.WITHDRAWAL, UUID.randomUUID(), "ARR-AB23CD45"));
    }
}
