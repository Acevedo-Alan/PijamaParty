package com.pijamaparty.store.backoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ContractTest;
import org.junit.jupiter.api.Test;

/** Contract of {@link OrderCounts} (T-E8-05): only the {@code PAID} orders wait to be prepared. */
public abstract class OrderCountsContractTest<T extends OrderCounts> extends ContractTest<T> {

    /** A paid order, waiting to be prepared. */
    protected abstract void givenPaid();

    /** An order in another status: awaiting payment, then being prepared. */
    protected abstract void givenAwaitingPayment();

    protected abstract void givenPreparing();

    @Test
    void givenNoOrders_whenCounted_thenNoneWait() {
        assertThat(subject.paidWaiting()).isZero();
    }

    @Test
    void givenTwoPaidOrdersAndOthers_whenCounted_thenTheTwoPaidWait() {
        givenPaid();
        givenPaid();
        givenAwaitingPayment();
        givenPreparing();

        assertThat(subject.paidWaiting()).isEqualTo(2);
    }
}
