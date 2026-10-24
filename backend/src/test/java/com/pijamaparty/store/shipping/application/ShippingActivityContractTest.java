package com.pijamaparty.store.shipping.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.activity.testing.ActivityPortContractTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Contract of {@link ShippingActivity}: every entry recorded is read back whole. */
public abstract class ShippingActivityContractTest<T extends ShippingActivity> extends ActivityPortContractTest<T> {

    @Override
    protected void record(ActivityEntry entry) {
        subject.record(entry);
    }

    @Override
    protected ActivityEntry entryWithChanges() {
        return new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.SHIPPING_ZONES_CHANGED,
            new ActivitySubject(SubjectType.SHIPPING, Optional.empty(), Optional.of("SANTA_FE")),
            List.of(FieldChange.of("cost", "$5.000", "$6.000")));
    }

    @Override
    protected ActivityEntry entryWithoutChanges() {
        return new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.FREE_SHIPPING_THRESHOLD_CHANGED,
            ActivitySubject.of(SubjectType.SHIPPING));
    }
}
