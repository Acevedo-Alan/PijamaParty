package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.activity.testing.ActivityPortContractTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.List;

/** Contract of {@link AccountActivity}: every entry recorded is read back whole. */
public abstract class AccountActivityContractTest<T extends AccountActivity> extends ActivityPortContractTest<T> {

    @Override
    protected void record(ActivityEntry entry) {
        subject.record(entry);
    }

    @Override
    protected ActivityEntry entryWithChanges() {
        return new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.ADMIN_SIGNED_IN,
            ActivitySubject.of(SubjectType.ADMINISTRATOR, RlsPolicyTester.ADMIN_ID.value()),
            List.of(FieldChange.of("note", null, "a field, so the changes are read back too")));
    }

    @Override
    protected ActivityEntry entryWithoutChanges() {
        return new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.RECOVERY_CODE_USED,
            ActivitySubject.of(SubjectType.ADMINISTRATOR, RlsPolicyTester.ADMIN_ID.value()));
    }
}
