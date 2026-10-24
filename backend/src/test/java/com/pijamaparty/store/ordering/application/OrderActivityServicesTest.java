package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.ordering.application.SaveWithdrawals.NewWithdrawal;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.WithdrawalCode;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-37 (T-E8-03): what the order and withdrawal use cases record in the activity log, and when they record nothing. */
class OrderActivityServicesTest {

    private static final Instant NOW = Instant.parse("2026-10-24T15:00:00Z");
    private static final UserId ADMIN_ID = UserId.of(UUID.randomUUID());
    private static final Actor ADMIN = Actor.admin(ADMIN_ID);

    private final InMemoryOrderStore orders = new InMemoryOrderStore();
    private final InMemoryStockReleases stock = new InMemoryStockReleases();
    private final InMemoryWithdrawalStore withdrawals = new InMemoryWithdrawalStore();
    private final InMemoryOrderActivity activity = new InMemoryOrderActivity();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ChangeOrderStatus changeStatus = new ChangeOrderStatusService(orders, orders, stock, stock,
        activity, clock);
    private final CorrectShipmentTrackingNumber correctTrackingNumber =
        new CorrectShipmentTrackingNumberService(orders, orders, activity, clock);
    private final ResolveWithdrawal resolveWithdrawal = new ResolveWithdrawalService(withdrawals, activity, clock);
    private final OrderId order = OrderId.of(UUID.randomUUID());

    @Test
    void givenAPreparingOrder_whenShipped_thenTheEntryHasItsNumberTheStatusesAndTheTrackingNumber() {
        long number = orders.add(order, OrderStatus.PREPARING, NOW);

        changeStatus.execute(new ChangeOrderStatus.Command(ADMIN, order, OrderStatus.PREPARING, OrderStatus.SHIPPED,
            Optional.of("CA123456789AR"), Optional.empty()));

        assertThat(activity.single()).isEqualTo(new ActivityEntry(ADMIN_ID, ActivityAction.ORDER_STATUS_CHANGED,
            ActivitySubject.of(SubjectType.ORDER, order.value(), "#" + number),
            List.of(FieldChange.of("status", "PREPARING", "SHIPPED"),
                FieldChange.of("trackingNumber", null, "CA123456789AR"))));
    }

    @Test
    void givenAPaidOrder_whenCancelledPuttingTheStockBack_thenTheEntrySaysSo() {
        orders.add(order, OrderStatus.PAID, NOW);

        changeStatus.execute(new ChangeOrderStatus.Command(ADMIN, order, OrderStatus.PAID, OrderStatus.CANCELLED,
            Optional.empty(), Optional.of(true)));

        assertThat(activity.single().changes()).containsExactly(FieldChange.of("status", "PAID", "CANCELLED"),
            FieldChange.of("restock", null, "true"));
    }

    @Test
    void givenAnOrderAwaitingPayment_whenCancelled_thenTheEntryHasOnlyTheStatuses() {
        orders.add(order, OrderStatus.AWAITING_PAYMENT, NOW);

        changeStatus.execute(new ChangeOrderStatus.Command(ADMIN, order, OrderStatus.AWAITING_PAYMENT,
            OrderStatus.CANCELLED, Optional.empty(), Optional.empty()));

        assertThat(activity.single().changes()).containsExactly(
            FieldChange.of("status", "AWAITING_PAYMENT", "CANCELLED"));
    }

    @Test
    void givenAnOrderThatChangedOrARefusedChange_whenExecuted_thenNothingIsRecorded() {
        orders.add(order, OrderStatus.PAID, NOW);

        changeStatus.execute(new ChangeOrderStatus.Command(ADMIN, order, OrderStatus.PREPARING, OrderStatus.SHIPPED,
            Optional.of("CA123456789AR"), Optional.empty()));
        changeStatus.execute(new ChangeOrderStatus.Command(ADMIN, order, OrderStatus.PAID, OrderStatus.CANCELLED,
            Optional.empty(), Optional.empty()));

        assertThat(activity.entries()).isEmpty();
    }

    @Test
    void givenAShippedOrder_whenItsTrackingNumberIsCorrected_thenTheEntryHasTheOldAndTheNewOne() {
        long number = orders.add(order, OrderStatus.PAID, NOW);
        orders.ship(order, "CA000000000AR");

        correctTrackingNumber.execute(new CorrectShipmentTrackingNumber.Command(ADMIN, order, "CA123456789AR"));

        assertThat(activity.single()).isEqualTo(new ActivityEntry(ADMIN_ID, ActivityAction.TRACKING_NUMBER_CORRECTED,
            ActivitySubject.of(SubjectType.ORDER, order.value(), "#" + number),
            List.of(FieldChange.of("trackingNumber", "CA000000000AR", "CA123456789AR"))));
    }

    @Test
    void givenAShippedOrder_whenCorrectedToTheSameNumberOrWhileNotShipped_thenNothingIsRecorded() {
        orders.add(order, OrderStatus.PAID, NOW);
        OrderId preparing = OrderId.of(UUID.randomUUID());
        orders.add(preparing, OrderStatus.PREPARING, NOW);
        orders.ship(order, "CA123456789AR");

        correctTrackingNumber.execute(new CorrectShipmentTrackingNumber.Command(ADMIN, order, " CA123456789AR "));
        correctTrackingNumber.execute(new CorrectShipmentTrackingNumber.Command(ADMIN, preparing, "CA1"));

        assertThat(activity.entries()).isEmpty();
    }

    @Test
    void givenAReceivedWithdrawal_whenResolved_thenTheEntryHasItsCodeAndResolvingItAgainRecordsNothing() {
        UUID request = UUID.randomUUID();
        withdrawals.insert(new NewWithdrawal(request, withdrawals.place(OrderStatus.PAID, NOW.minusSeconds(3600),
            Optional.empty()).id(), new WithdrawalCode("ARR-7K2M9QXZ"), false, NOW.minusSeconds(60)));

        resolveWithdrawal.execute(new ResolveWithdrawal.Command(ADMIN, request));
        resolveWithdrawal.execute(new ResolveWithdrawal.Command(ADMIN, request));

        assertThat(activity.single()).isEqualTo(new ActivityEntry(ADMIN_ID, ActivityAction.WITHDRAWAL_RESOLVED,
            ActivitySubject.of(SubjectType.WITHDRAWAL, request, "ARR-7K2M9QXZ")));
    }
}
