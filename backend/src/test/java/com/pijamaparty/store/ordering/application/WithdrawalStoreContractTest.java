package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.api.WithdrawalNotices.WithdrawalNotice;
import com.pijamaparty.store.ordering.application.SaveWithdrawals.NewWithdrawal;
import com.pijamaparty.store.ordering.application.WithdrawableOrders.WithdrawableOrder;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.WithdrawalCode;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link WithdrawableOrders}, {@link ActiveWithdrawals}, {@link SaveWithdrawals} and
 * {@link WithdrawalNoticeDetails} (UC-29, T-E3-25): an order found by its number and email, its active request, a code
 * taken once, and what the emails of a request tell.
 */
public abstract class WithdrawalStoreContractTest<T extends WithdrawableOrders & ActiveWithdrawals & SaveWithdrawals
    & WithdrawalNoticeDetails> extends ContractTest<T> {

    protected static final Instant PLACED_AT = Instant.parse("2026-10-01T13:00:00Z");
    protected static final Instant DELIVERED_AT = Instant.parse("2026-10-05T18:30:00Z");
    protected static final Instant NOW = Instant.parse("2026-10-08T15:00:00Z");
    /** The email of every order {@link #placed}, normalized. */
    protected static final String EMAIL = "ana@example.com";

    /**
     * An order of Ana Pérez ({@code ana@example.com}) in {@code status}, placed at {@code createdAt}; delivered at
     * {@code deliveredAt} when {@code DELIVERED}.
     */
    protected abstract WithdrawableOrder placed(OrderStatus status, Instant createdAt, Optional<Instant> deliveredAt);

    /** The administrator resolved the request (T-E3-26). */
    protected abstract void resolved(UUID request, Instant at);

    @Test
    void givenAPaidOrder_whenLockedByItsNumberAndEmail_thenItComesWithItsStatusAndDates() {
        WithdrawableOrder order = placed(OrderStatus.PAID, PLACED_AT, Optional.empty());
        persistAndForget();

        assertThat(subject.lock(order.number(), EMAIL)).contains(new WithdrawableOrder(order.id(), order.number(),
            OrderStatus.PAID, PLACED_AT, Optional.empty()));
    }

    @Test
    void givenADeliveredOrder_whenLockedByItsNumberAndEmail_thenItComesWithItsDeliveryDate() {
        WithdrawableOrder order = placed(OrderStatus.DELIVERED, PLACED_AT, Optional.of(DELIVERED_AT));
        persistAndForget();

        assertThat(subject.lock(order.number(), EMAIL)).map(WithdrawableOrder::deliveredAt)
            .contains(Optional.of(DELIVERED_AT));
    }

    @Test
    void givenNoOrderWithTheNumber_whenLocked_thenThereIsNone() {
        WithdrawableOrder order = placed(OrderStatus.PAID, PLACED_AT, Optional.empty());
        persistAndForget();

        assertThat(subject.lock(order.number() + 1000, EMAIL)).isEmpty();
    }

    @Test
    void givenTheNumberOfAnOrderWithAnotherEmail_whenLocked_thenThereIsNoneAsForAnUnknownNumber() {
        WithdrawableOrder order = placed(OrderStatus.PAID, PLACED_AT, Optional.empty());
        persistAndForget();

        assertThat(subject.lock(order.number(), "otra@example.com")).isEmpty();
    }

    @Test
    void givenAnOrderWithoutRequests_whenAskedForItsActiveOne_thenThereIsNone() {
        WithdrawableOrder order = placed(OrderStatus.PAID, PLACED_AT, Optional.empty());
        persistAndForget();

        assertThat(subject.codeOf(order.id())).isEmpty();
    }

    @Test
    void givenARecordedRequest_whenRead_thenItIsTheActiveOneAndItsNoticeTellsTheOrderTheBuyerAndTheTerm() {
        WithdrawableOrder order = placed(OrderStatus.PAID, PLACED_AT, Optional.empty());
        var withdrawal = new NewWithdrawal(UUID.randomUUID(), order.id(), new WithdrawalCode("ARR-7K2M9QXZ"), true, NOW);

        assertThat(subject.insert(withdrawal)).isTrue();
        persistAndForget();

        assertThat(subject.codeOf(order.id())).contains(new WithdrawalCode("ARR-7K2M9QXZ"));
        assertThat(subject.noticeOf(withdrawal.id())).contains(new WithdrawalNotice(withdrawal.id(), "ARR-7K2M9QXZ",
            order.number(), "Ana", "Pérez", "ana@example.com", true));
    }

    @Test
    void givenACodeAlreadyTakenByAnotherOrder_whenInserted_thenNothingIsRecorded() {
        WithdrawableOrder first = placed(OrderStatus.PAID, PLACED_AT, Optional.empty());
        WithdrawableOrder second = placed(OrderStatus.PAID, PLACED_AT, Optional.empty());
        var code = new WithdrawalCode("ARR-7K2M9QXZ");
        subject.insert(new NewWithdrawal(UUID.randomUUID(), first.id(), code, false, NOW));
        var repeated = new NewWithdrawal(UUID.randomUUID(), second.id(), code, false, NOW);

        assertThat(subject.insert(repeated)).isFalse();
        persistAndForget();

        assertThat(subject.codeOf(second.id())).isEmpty();
        assertThat(subject.noticeOf(repeated.id())).isEmpty();
    }

    @Test
    void givenAResolvedRequest_whenAskedForTheActiveOne_thenThereIsNoneAndAnotherCanBeRecorded() {
        WithdrawableOrder order = placed(OrderStatus.DELIVERED, PLACED_AT, Optional.of(DELIVERED_AT));
        var first = new NewWithdrawal(UUID.randomUUID(), order.id(), new WithdrawalCode("ARR-7K2M9QXZ"), false, NOW);
        subject.insert(first);
        resolved(first.id(), NOW.plusSeconds(3600));
        persistAndForget();

        assertThat(subject.codeOf(order.id())).isEmpty();

        var second = new NewWithdrawal(UUID.randomUUID(), order.id(), new WithdrawalCode("ARR-22222222"), true,
            NOW.plusSeconds(7200));
        assertThat(subject.insert(second)).isTrue();
        assertThat(subject.codeOf(order.id())).contains(new WithdrawalCode("ARR-22222222"));
    }

    @Test
    void givenNoSuchRequest_whenItsNoticeIsRead_thenThereIsNone() {
        assertThat(subject.noticeOf(UUID.randomUUID())).isEmpty();
    }
}
