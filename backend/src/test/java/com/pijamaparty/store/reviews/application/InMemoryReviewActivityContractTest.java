package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.activity.api.ActivityEntry;
import java.util.List;

class InMemoryReviewActivityContractTest extends ReviewActivityContractTest<InMemoryReviewActivity> {

    @Override
    protected InMemoryReviewActivity createSubject() {
        return new InMemoryReviewActivity();
    }

    @Override
    protected List<ActivityEntry> recorded() {
        return subject.entries();
    }
}
