package com.pijamaparty.store.promotions.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.activity.testing.ActivityPortContractTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.List;
import java.util.UUID;

/** Contract of {@link CouponActivity}: every entry recorded is read back whole. */
public abstract class CouponActivityContractTest<T extends CouponActivity> extends ActivityPortContractTest<T> {

    @Override
    protected void record(ActivityEntry entry) {
        subject.record(entry);
    }

    @Override
    protected ActivityEntry entryWithChanges() {
        return new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.COUPON_UPDATED,
            ActivitySubject.of(SubjectType.COUPON, UUID.randomUUID(), "VERANO15"),
            List.of(FieldChange.of("value", "10%", "15%"), FieldChange.of("maxUses", "100", null)));
    }

    @Override
    protected ActivityEntry entryWithoutChanges() {
        return new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.COUPON_DELETED,
            ActivitySubject.of(SubjectType.COUPON, UUID.randomUUID(), "INVIERNO"));
    }
}
