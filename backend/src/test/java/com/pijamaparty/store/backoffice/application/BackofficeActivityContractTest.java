package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.activity.testing.ActivityPortContractTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.List;
import java.util.UUID;

/** Contract of {@link BackofficeActivity}: every entry recorded is read back whole. */
public abstract class BackofficeActivityContractTest<T extends BackofficeActivity> extends ActivityPortContractTest<T> {

    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Override
    protected void record(ActivityEntry entry) {
        subject.record(entry);
    }

    @Override
    protected ActivityEntry entryWithChanges() {
        return new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.CUSTOMER_VIEWED,
            ActivitySubject.of(SubjectType.CUSTOMER, CUSTOMER),
            List.of(FieldChange.of("note", null, "a field, so the changes are read back too")));
    }

    @Override
    protected ActivityEntry entryWithoutChanges() {
        return new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.CUSTOMER_VIEWED,
            ActivitySubject.of(SubjectType.CUSTOMER, CUSTOMER));
    }
}
