package com.pijamaparty.store.backoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.ordering.api.OrderWorkload.RecentOrder;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link OrderCounts} (T-E8-05, UC-33): the {@code PAID} orders wait to be prepared, the
 * {@code PREPARING} ones are being prepared, the received withdrawal requests wait, the newest orders come first,
 * and paid, preparing and shipped orders are open.
 */
public abstract class OrderCountsContractTest<T extends OrderCounts> extends ContractTest<T> {

    protected static final Instant NOON = Instant.parse("2026-10-15T15:00:00Z");

    /** An order of Ana Pérez in {@code status}, created at {@code createdAt}; answers its id. */
    protected abstract UUID givenOrder(Status status, Instant createdAt);

    /** A paid order with a withdrawal request still received. */
    protected abstract void givenReceivedWithdrawal();

    @Test
    void givenNoOrders_whenCounted_thenNoneWait() {
        assertThat(subject.paidWaiting()).isZero();
        assertThat(subject.preparing()).isZero();
        assertThat(subject.receivedWithdrawals()).isZero();
        assertThat(subject.newest(5)).isEmpty();
    }

    @Test
    void givenTwoPaidOrdersAndOthers_whenCounted_thenTheTwoPaidWaitAndTheOnePreparingIsBeingPrepared() {
        givenOrder(Status.PAID, NOON);
        givenOrder(Status.PAID, NOON);
        givenOrder(Status.AWAITING_PAYMENT, NOON);
        givenOrder(Status.PREPARING, NOON);
        persistAndForget();

        assertThat(subject.paidWaiting()).isEqualTo(2);
        assertThat(subject.preparing()).isEqualTo(1);
    }

    @Test
    void givenTwoReceivedWithdrawals_whenCounted_thenBothWait() {
        givenReceivedWithdrawal();
        givenReceivedWithdrawal();
        persistAndForget();

        assertThat(subject.receivedWithdrawals()).isEqualTo(2);
    }

    @Test
    void givenThreeOrders_whenTheNewestTwoAreAsked_thenTheyComeNewestFirstWithTheirBuyer() {
        givenOrder(Status.PAID, NOON);
        UUID middle = givenOrder(Status.DELIVERED, NOON.plusSeconds(60));
        UUID newest = givenOrder(Status.AWAITING_PAYMENT, NOON.plusSeconds(120));
        persistAndForget();

        List<RecentOrder> recent = subject.newest(2);

        assertThat(recent).extracting(RecentOrder::id).containsExactly(newest, middle);
        assertThat(recent).extracting(RecentOrder::buyerName).containsOnly("Ana Pérez");
    }

    @Test
    void givenOrdersInSeveralStatuses_whenAskedWhichAreOpen_thenPaidPreparingAndShippedAre() {
        UUID paid = givenOrder(Status.PAID, NOON);
        UUID shipped = givenOrder(Status.SHIPPED, NOON);
        UUID delivered = givenOrder(Status.DELIVERED, NOON);
        persistAndForget();

        assertThat(subject.openAmong(List.of(paid, shipped, delivered, UUID.randomUUID())))
            .containsExactlyInAnyOrder(paid, shipped);
    }
}
