package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.ordering.api.AdminOrderDetails.AdminOrder;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.Buyer;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.ordering.api.PaidOrders.Address;
import com.pijamaparty.store.ordering.api.PaidOrders.Line;
import com.pijamaparty.store.ordering.application.OrderStatuses.LockedOrder;
import com.pijamaparty.store.ordering.application.SaveOrders.AppliedCoupon;
import com.pijamaparty.store.ordering.application.SaveOrders.NewOrder;
import com.pijamaparty.store.ordering.domain.BuyerDetails;
import com.pijamaparty.store.ordering.domain.OrderAmounts;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.ordering.domain.ShipmentTrackingNumber;
import com.pijamaparty.store.ordering.domain.ShippingAddress;
import com.pijamaparty.store.ordering.domain.StatusChange;
import com.pijamaparty.store.ordering.domain.StatusChange.StockEffect;
import com.pijamaparty.store.ordering.domain.StockHold;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.shipping.api.Province;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link OrderPages}, {@link OrderStatusCounts}, {@link AdminOrderViews}, {@link OrderStatuses}, {@link StatusChanges},
 * {@link ShipmentTrackingNumbers} and {@link LatePayments} over orders placed through {@link SaveOrders} (UC-18,
 * RNF-20.5): what is written shows in the back-office list and detail.
 */
public abstract class AdminOrdersContractTest<T extends SaveOrders & OrderNumbers & OrderPages & OrderStatusCounts
    & AdminOrderViews
    & OrderStatuses & OrderTransitions & StatusChanges & ShipmentTrackingNumbers & LatePayments>
    extends ContractTest<T> {

    protected static final Instant NOW = Instant.parse("2026-10-13T15:00:00.123456Z");

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final List<OrderLine> LINES = List.of(
        new OrderLine(UUID.randomUUID(), "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000), 2),
        new OrderLine(UUID.randomUUID(), "CS-S-GRIS", "Camisón Sol", "S", "Gris", Money.of(1_000_000), 1));

    /** The buyer that holds {@code token} is the one who inserts its order (RNF-27). */
    protected void tokenIssued(TrackingToken token) {
    }

    @Test
    void givenAPlacedOrderWithACoupon_whenViewed_thenItIsAsPlacedAwaitingPayment() {
        NewOrder order = placed(NOW, Optional.of(new AppliedCoupon(UUID.randomUUID(), "VERANO10")));

        assertThat(subject.adminViewOf(order.id())).contains(new AdminOrder(order.id().value(), order.number(),
            Status.AWAITING_PAYMENT, List.of(), NOW, NOW, Optional.empty(),
            new Buyer("Ana María", "Pérez", "ana@example.com", "3425551234", "12345678"),
            new Address("San Martín", "1234", Optional.of("3"), Optional.of("B"), "Santa Fe", "SANTA_FE", "S3000ABC"),
            List.of(new Line("Pijama Luna", "M", "Rosa", 2, Money.of(2_000_000), Money.of(4_000_000)),
                new Line("Camisón Sol", "S", "Gris", 1, Money.of(1_000_000), Money.of(1_000_000))),
            Money.of(5_000_000), Money.of(500_000), Optional.of("VERANO10"), Money.of(300_000), Money.of(4_800_000),
            "MERCADOPAGO", Optional.empty(), false));
    }

    @Test
    void givenNoSuchOrder_whenViewedOrLocked_thenThereIsNone() {
        OrderId unknown = OrderId.of(UUID.randomUUID());

        assertThat(subject.adminViewOf(unknown)).isEmpty();
        assertThat(subject.lockForChange(unknown)).isEmpty();
    }

    @Test
    void givenOrdersInSeveralStatuses_whenPaged_thenTheNewestComeFirstAndAStatusKeepsOnlyItsOwn() {
        NewOrder oldest = placed(NOW, Optional.empty());
        NewOrder paid = placed(NOW.plusSeconds(1), Optional.empty());
        NewOrder newest = placed(NOW.plusSeconds(2), Optional.empty());
        subject.transition(paid.id(), OrderStatus.AWAITING_PAYMENT, OrderStatus.PAID, NOW.plusSeconds(3));
        persistAndForget();

        assertThat(subject.page(Optional.empty(), 0, 10)).extracting(OrderSummary::id)
            .containsExactly(newest.id(), paid.id(), oldest.id());
        assertThat(subject.page(Optional.empty(), 1, 1)).extracting(OrderSummary::id).containsExactly(paid.id());
        assertThat(subject.page(Optional.of(OrderStatus.PAID), 0, 10)).singleElement().isEqualTo(new OrderSummary(
            paid.id(), paid.number(), NOW.plusSeconds(1), OrderStatus.PAID, "Ana María Pérez", "Santa Fe",
            Province.SANTA_FE, Money.of(5_300_000)));
        assertThat(subject.page(Optional.of(OrderStatus.SHIPPED), 0, 10)).isEmpty();
    }

    @Test
    void givenOrdersInSeveralStatuses_whenCounted_thenEachStatusCountsOnlyItsOwn() {
        placed(NOW, Optional.empty());
        NewOrder first = placed(NOW.plusSeconds(1), Optional.empty());
        NewOrder second = placed(NOW.plusSeconds(2), Optional.empty());
        subject.transition(first.id(), OrderStatus.AWAITING_PAYMENT, OrderStatus.PAID, NOW.plusSeconds(3));
        subject.transition(second.id(), OrderStatus.AWAITING_PAYMENT, OrderStatus.PAID, NOW.plusSeconds(3));
        persistAndForget();

        assertThat(subject.count(OrderStatus.PAID)).isEqualTo(2);
        assertThat(subject.count(OrderStatus.AWAITING_PAYMENT)).isEqualTo(1);
        assertThat(subject.count(OrderStatus.SHIPPED)).isZero();
    }

    @Test
    void givenAPaidOrder_whenTheAdministratorTakesItToDelivered_thenItsTrackingNumberAndDeliveryShow() {
        NewOrder order = placed(NOW, Optional.empty());
        subject.transition(order.id(), OrderStatus.AWAITING_PAYMENT, OrderStatus.PAID, NOW.plusSeconds(1));
        Instant delivered = NOW.plusSeconds(4);

        assertThat(subject.change(order.id(), change(OrderStatus.PAID, OrderStatus.PREPARING, Optional.empty()),
            NOW.plusSeconds(2))).isTrue();
        assertThat(subject.change(order.id(), change(OrderStatus.PREPARING, OrderStatus.SHIPPED,
            Optional.of(new ShipmentTrackingNumber("CA123456789AR"))), NOW.plusSeconds(3))).isTrue();
        assertThat(subject.change(order.id(), change(OrderStatus.SHIPPED, OrderStatus.DELIVERED, Optional.empty()),
            delivered)).isTrue();
        persistAndForget();

        assertThat(subject.lockForChange(order.id())).map(LockedOrder::status).contains(OrderStatus.DELIVERED);
        assertThat(subject.adminViewOf(order.id())).hasValueSatisfying(view -> {
            assertThat(view.status()).isEqualTo(Status.DELIVERED);
            assertThat(view.shipmentTrackingNumber()).contains("CA123456789AR");
            assertThat(view.deliveredAt()).contains(delivered);
            assertThat(view.updatedAt()).isEqualTo(delivered);
        });
    }

    @Test
    void givenAnOrderNoLongerInTheStatusSeen_whenChanged_thenNothingChanges() {
        NewOrder order = placed(NOW, Optional.empty());

        assertThat(subject.change(order.id(), change(OrderStatus.PAID, OrderStatus.PREPARING, Optional.empty()),
            NOW.plusSeconds(1))).isFalse();
        assertThat(subject.change(OrderId.of(UUID.randomUUID()), change(OrderStatus.AWAITING_PAYMENT,
            OrderStatus.CANCELLED, Optional.empty()), NOW.plusSeconds(1))).isFalse();
        persistAndForget();

        assertThat(subject.lockForChange(order.id())).map(LockedOrder::status).contains(OrderStatus.AWAITING_PAYMENT);
    }

    @Test
    void givenAChangeOutsideTheStateDiagram_whenApplied_thenItIsRefused() {
        NewOrder order = placed(NOW, Optional.empty());

        assertThatThrownBy(() -> subject.change(order.id(),
            change(OrderStatus.AWAITING_PAYMENT, OrderStatus.DELIVERED, Optional.empty()), NOW))
            .isInstanceOf(RuntimeException.class).hasMessageContaining("cannot go from AWAITING_PAYMENT to DELIVERED");
        assertThat(subject.lockForChange(order.id())).map(LockedOrder::status).contains(OrderStatus.AWAITING_PAYMENT);
    }

    @Test
    void givenAPlacedAndAShippedOrder_whenLockedForAChange_thenEachHasItsNumberStatusAndTrackingNumberIfShipped() {
        NewOrder placed = placed(NOW, Optional.empty());
        NewOrder shipped = shipped("CA123456789AR");
        persistAndForget();

        assertThat(subject.lockForChange(placed.id()))
            .contains(new LockedOrder(placed.number(), OrderStatus.AWAITING_PAYMENT, Optional.empty()));
        assertThat(subject.lockForChange(shipped.id())).contains(new LockedOrder(shipped.number(), OrderStatus.SHIPPED,
            Optional.of(new ShipmentTrackingNumber("CA123456789AR"))));
    }

    @Test
    void givenAShippedOrder_whenItsTrackingNumberIsCorrected_thenTheNewOneShowsAndOnlyWhileShipped() {
        NewOrder order = shipped("CA000000000AR");

        assertThat(subject.correct(order.id(), new ShipmentTrackingNumber("CA123456789AR"), NOW.plusSeconds(10)))
            .isTrue();
        subject.change(order.id(), change(OrderStatus.SHIPPED, OrderStatus.DELIVERED, Optional.empty()),
            NOW.plusSeconds(11));
        assertThat(subject.correct(order.id(), new ShipmentTrackingNumber("XX"), NOW.plusSeconds(12))).isFalse();
        persistAndForget();

        assertThat(subject.adminViewOf(order.id()).flatMap(AdminOrder::shipmentTrackingNumber))
            .contains("CA123456789AR");
    }

    @Test
    void givenAnExpiredOrder_whenPaidAfterClosingTwice_thenTheDetailSaysSo() {
        NewOrder order = placed(NOW, Optional.empty());
        subject.transition(order.id(), OrderStatus.AWAITING_PAYMENT, OrderStatus.EXPIRED, NOW.plusSeconds(1));

        subject.paidAfterClosing(order.id(), NOW.plusSeconds(2));
        subject.paidAfterClosing(order.id(), NOW.plusSeconds(3));
        persistAndForget();

        assertThat(subject.adminViewOf(order.id())).hasValueSatisfying(view -> {
            assertThat(view.paidAfterClosing()).isTrue();
            assertThat(view.updatedAt()).isEqualTo(NOW.plusSeconds(2));
        });
    }

    @Test
    void givenAnOrderStillOpen_whenPaidAfterClosing_thenNothingChanges() {
        NewOrder order = placed(NOW, Optional.empty());

        subject.paidAfterClosing(order.id(), NOW.plusSeconds(2));
        persistAndForget();

        assertThat(subject.adminViewOf(order.id()).map(AdminOrder::paidAfterClosing)).contains(false);
    }

    private NewOrder shipped(String trackingNumber) {
        NewOrder order = placed(NOW, Optional.empty());
        subject.transition(order.id(), OrderStatus.AWAITING_PAYMENT, OrderStatus.PAID, NOW.plusSeconds(1));
        subject.change(order.id(), change(OrderStatus.PAID, OrderStatus.PREPARING, Optional.empty()),
            NOW.plusSeconds(2));
        subject.change(order.id(), change(OrderStatus.PREPARING, OrderStatus.SHIPPED,
            Optional.of(new ShipmentTrackingNumber(trackingNumber))), NOW.plusSeconds(3));
        return order;
    }

    private static StatusChange change(OrderStatus from, OrderStatus to, Optional<ShipmentTrackingNumber> number) {
        return new StatusChange(from, to, number, StockEffect.NONE);
    }

    /** A guest's order placed at {@code createdAt}: ARS 50,000 of lines, ARS 3,000 of shipping. */
    protected NewOrder placed(Instant createdAt, Optional<AppliedCoupon> coupon) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        var token = new TrackingToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        tokenIssued(token);
        Money discount = coupon.isPresent() ? Money.of(500_000) : Money.zero();
        NewOrder order = new NewOrder(OrderId.of(UUID.randomUUID()), subject.next(), token.hash(), Optional.empty(),
            new BuyerDetails("Ana María", "Pérez", "ana@example.com", "3425551234", "12345678"),
            new ShippingAddress("San Martín", "1234", Optional.of("3"), Optional.of("B"), "Santa Fe", "S3000ABC"),
            Province.SANTA_FE, LINES, OrderAmounts.of(Money.of(5_000_000), discount, Money.of(300_000)), coupon,
            PaymentProvider.MERCADOPAGO, "2026-10", createdAt, StockHold.expiryOf(createdAt));
        subject.insert(order);
        persistAndForget();
        return order;
    }
}
