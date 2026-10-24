package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.api.OrderWithdrawals.ActiveWithdrawal;
import com.pijamaparty.store.ordering.application.SaveWithdrawals.NewWithdrawal;
import com.pijamaparty.store.ordering.application.WithdrawableOrders.WithdrawableOrder;
import com.pijamaparty.store.ordering.application.WithdrawalResolutions.LockedWithdrawal;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.WithdrawalCode;
import com.pijamaparty.store.ordering.domain.WithdrawalStatus;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link WithdrawalPages}, {@link ActiveWithdrawalViews} and {@link WithdrawalResolutions} (UC-29,
 * T-E3-26): the requests recorded by the form show in the back-office list and on their order, and a resolved one
 * leaves the active ones.
 */
public abstract class AdminWithdrawalsContractTest<T extends WithdrawalPages & ActiveWithdrawalViews
    & WithdrawalResolutions> extends ContractTest<T> {

    protected static final Instant PLACED_AT = Instant.parse("2026-10-01T13:00:00Z");
    protected static final Instant NOW = Instant.parse("2026-10-08T15:00:00Z");

    /** An order of Ana Pérez ({@code ana@example.com}) in {@code status} (neither shipped nor delivered). */
    protected abstract WithdrawableOrder placed(OrderStatus status);

    /** The form recorded the request, {@code RECEIVED}. */
    protected abstract void requested(NewWithdrawal withdrawal);

    @Test
    void givenRequestsInBothStatuses_whenPaged_thenTheNewestComeFirstAndAStatusKeepsOnlyItsOwn() {
        WithdrawableOrder paid = placed(OrderStatus.PAID);
        WithdrawableOrder preparing = placed(OrderStatus.PREPARING);
        NewWithdrawal older = withdrawal(paid, "ARR-22222222", false, NOW);
        NewWithdrawal newer = withdrawal(preparing, "ARR-33333333", true, NOW.plusSeconds(60));
        requested(older);
        requested(newer);
        subject.lockForResolution(older.id());
        subject.resolve(older.id(), NOW.plusSeconds(3600));
        persistAndForget();

        assertThat(subject.page(Optional.empty(), 0, 10)).containsExactly(
            new WithdrawalSummary(newer.id(), newer.code(), preparing.id(), preparing.number(), OrderStatus.PREPARING,
                "Ana Pérez", newer.createdAt(), true, WithdrawalStatus.RECEIVED, Optional.empty()),
            new WithdrawalSummary(older.id(), older.code(), paid.id(), paid.number(), OrderStatus.PAID, "Ana Pérez",
                older.createdAt(), false, WithdrawalStatus.RESOLVED, Optional.of(NOW.plusSeconds(3600))));
        assertThat(subject.page(Optional.of(WithdrawalStatus.RECEIVED), 0, 10)).extracting(WithdrawalSummary::id)
            .containsExactly(newer.id());
        assertThat(subject.page(Optional.of(WithdrawalStatus.RESOLVED), 0, 10)).extracting(WithdrawalSummary::id)
            .containsExactly(older.id());
    }

    @Test
    void givenRequestsAtTheSameTime_whenPaged_thenTheyComeByCodeAndTheOffsetSkips() {
        List.of("ARR-44444444", "ARR-22222222", "ARR-33333333")
            .forEach(code -> requested(withdrawal(placed(OrderStatus.PAID), code, false, NOW)));
        persistAndForget();

        assertThat(subject.page(Optional.empty(), 1, 10)).extracting(summary -> summary.code().value())
            .containsExactly("ARR-33333333", "ARR-44444444");
        assertThat(subject.page(Optional.empty(), 0, 1)).extracting(summary -> summary.code().value())
            .containsExactly("ARR-22222222");
    }

    @Test
    void givenAReceivedRequest_whenItsOrderIsViewed_thenItIsTheActiveOne() {
        WithdrawableOrder order = placed(OrderStatus.PAID);
        WithdrawableOrder other = placed(OrderStatus.PAID);
        NewWithdrawal withdrawal = withdrawal(order, "ARR-7K2M9QXZ", true, NOW);
        requested(withdrawal);
        persistAndForget();

        assertThat(subject.activeViewOf(order.id()))
            .contains(new ActiveWithdrawal(withdrawal.id(), "ARR-7K2M9QXZ", true, NOW));
        assertThat(subject.activeViewOf(other.id())).isEmpty();
    }

    @Test
    void givenAReceivedRequest_whenLockedAndResolved_thenItIsResolvedAtThatTimeAndNoLongerActive() {
        WithdrawableOrder order = placed(OrderStatus.PAID);
        NewWithdrawal withdrawal = withdrawal(order, "ARR-7K2M9QXZ", false, NOW);
        requested(withdrawal);

        assertThat(subject.lockForResolution(withdrawal.id()))
            .contains(new LockedWithdrawal(withdrawal.code(), WithdrawalStatus.RECEIVED, NOW, Optional.empty()));
        subject.resolve(withdrawal.id(), NOW.plusSeconds(3600));
        persistAndForget();

        assertThat(subject.lockForResolution(withdrawal.id()))
            .contains(new LockedWithdrawal(withdrawal.code(), WithdrawalStatus.RESOLVED, NOW,
                Optional.of(NOW.plusSeconds(3600))));
        assertThat(subject.activeViewOf(order.id())).isEmpty();
    }

    @Test
    void givenNoSuchRequest_whenLockedForResolution_thenThereIsNone() {
        assertThat(subject.lockForResolution(UUID.randomUUID())).isEmpty();
    }

    private static NewWithdrawal withdrawal(WithdrawableOrder order, String code, boolean outOfTerm, Instant at) {
        return new NewWithdrawal(UUID.randomUUID(), order.id(), new WithdrawalCode(code), outOfTerm, at);
    }
}
