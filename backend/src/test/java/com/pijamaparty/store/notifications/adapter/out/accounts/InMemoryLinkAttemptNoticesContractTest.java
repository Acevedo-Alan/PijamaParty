package com.pijamaparty.store.notifications.adapter.out.accounts;

import com.pijamaparty.store.accounts.api.LinkAttemptNoticesContractTest;
import java.util.UUID;

/** The fake the notice composer is tested with behaves as the real recipients. */
class InMemoryLinkAttemptNoticesContractTest extends LinkAttemptNoticesContractTest<InMemoryLinkAttemptNotices> {

    @Override
    protected InMemoryLinkAttemptNotices createSubject() {
        return new InMemoryLinkAttemptNotices();
    }

    @Override
    protected UUID customer(String email, String name) {
        return subject.customer(email, name);
    }

    @Override
    protected UUID administrator(String email) {
        return subject.administrator();
    }
}
