package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.activity.testing.ActivityPortContractTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.List;
import java.util.UUID;

/** Contract of {@link ReviewActivity}: every entry recorded is read back whole. */
public abstract class ReviewActivityContractTest<T extends ReviewActivity> extends ActivityPortContractTest<T> {

    @Override
    protected void record(ActivityEntry entry) {
        subject.record(entry);
    }

    @Override
    protected ActivityEntry entryWithChanges() {
        return new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.REVIEW_APPROVED,
            ActivitySubject.of(SubjectType.REVIEW, UUID.randomUUID(), "Pijama Luna"), List.of(FieldChange.of(
                "status", "PENDING", "APPROVED")));
    }

    @Override
    protected ActivityEntry entryWithoutChanges() {
        return new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.REVIEW_DELETED,
            ActivitySubject.of(SubjectType.REVIEW, UUID.randomUUID()));
    }
}
