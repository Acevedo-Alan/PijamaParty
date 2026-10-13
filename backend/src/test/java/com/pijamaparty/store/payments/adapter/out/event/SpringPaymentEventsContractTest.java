package com.pijamaparty.store.payments.adapter.out.event;

import com.pijamaparty.store.payments.application.PaymentEventsContractTest;
import java.util.ArrayList;
import java.util.List;

/** Plain JUnit: the publisher Spring would inject only records what it gets. */
class SpringPaymentEventsContractTest extends PaymentEventsContractTest<SpringPaymentEvents> {

    private final List<Object> published = new ArrayList<>();

    @Override
    protected SpringPaymentEvents createSubject() {
        return new SpringPaymentEvents(published::add);
    }

    @Override
    protected List<Object> published() {
        return published;
    }
}
