package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.application.SaveOrders.AppliedCoupon;
import com.pijamaparty.store.ordering.application.SaveOrders.NewOrder;
import com.pijamaparty.store.ordering.application.TrackedOrders.TrackedOrder;
import com.pijamaparty.store.ordering.domain.BuyerDetails;
import com.pijamaparty.store.ordering.domain.OrderAmounts;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.ordering.domain.ShippingAddress;
import com.pijamaparty.store.ordering.domain.StockHold;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.shipping.api.Province;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link TrackedOrders} (UC-14, T-E3-08): the order of a tracking link, found by its token. */
public abstract class TrackedOrdersContractTest<T extends SaveOrders & OrderNumbers & TrackedOrders>
    extends ContractTest<T> {

    protected static final Instant NOW = Instant.parse("2026-10-13T15:00:00.123456Z");

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final List<OrderLine> LINES = List.of(
        new OrderLine(UUID.randomUUID(), "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000), 2),
        new OrderLine(UUID.randomUUID(), "CS-S-GRIS", "Camisón Sol", "S", "Gris", Money.of(1_000_000), 1));

    /** The buyer that holds {@code token} is the one who inserts its order (RNF-27). */
    protected void tokenIssued(TrackingToken token) {
    }

    /** The order went through UC-18 up to {@code SHIPPED} with this shipment tracking number. */
    protected abstract void shipped(OrderId order, String shipmentTrackingNumber);

    @Test
    void givenAPlacedOrderWithACoupon_whenFoundByItsToken_thenItIsAsPlacedAwaitingPaymentAndNotShipped() {
        TrackingToken token = newToken();
        NewOrder order = newOrder(token, Optional.empty(), Optional.of(new AppliedCoupon(UUID.randomUUID(), "VERANO10")));
        subject.insert(order);
        persistAndForget();

        assertThat(subject.find(token)).contains(new TrackedOrder(order.id(), order.number(),
            OrderStatus.AWAITING_PAYMENT, NOW, "Ana María Pérez", "Santa Fe", Province.SANTA_FE, LINES,
            OrderAmounts.of(Money.of(5_000_000), Money.of(500_000), Money.of(300_000)), Optional.of("VERANO10"),
            StockHold.expiryOf(NOW), Optional.empty()));
    }

    @Test
    void givenACustomersOrderWithoutCoupon_whenFoundByItsToken_thenItHasNoCouponCode() {
        TrackingToken token = newToken();
        NewOrder order = newOrder(token, Optional.of(UserId.of(UUID.randomUUID())), Optional.empty());
        subject.insert(order);
        persistAndForget();

        assertThat(subject.find(token)).hasValueSatisfying(found -> {
            assertThat(found.id()).isEqualTo(order.id());
            assertThat(found.couponCode()).isEmpty();
            assertThat(found.amounts().discount()).isEqualTo(Money.zero());
        });
    }

    @Test
    void givenAShippedOrder_whenFoundByItsToken_thenItHasItsShipmentTrackingNumber() {
        TrackingToken token = newToken();
        NewOrder order = newOrder(token, Optional.empty(), Optional.empty());
        subject.insert(order);
        shipped(order.id(), "CA123456789AR");
        persistAndForget();

        assertThat(subject.find(token)).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(found.shipmentTrackingNumber()).contains("CA123456789AR");
        });
    }

    @Test
    void givenTwoOrders_whenFoundByTheTokenOfOne_thenOnlyThatOneIsAnswered() {
        TrackingToken first = newToken();
        TrackingToken second = newToken();
        NewOrder firstOrder = newOrder(first, Optional.empty(), Optional.empty());
        NewOrder secondOrder = newOrder(second, Optional.empty(), Optional.empty());
        subject.insert(firstOrder);
        subject.insert(secondOrder);
        persistAndForget();

        assertThat(subject.find(second).map(TrackedOrder::id)).contains(secondOrder.id());
    }

    @Test
    void givenATokenOfNoOrder_whenFound_thenThereIsNone() {
        assertThat(subject.find(newToken())).isEmpty();
    }

    protected TrackingToken newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        var token = new TrackingToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        tokenIssued(token);
        return token;
    }

    protected NewOrder newOrder(TrackingToken token, Optional<UserId> userId, Optional<AppliedCoupon> coupon) {
        Money discount = coupon.isPresent() ? Money.of(500_000) : Money.zero();
        return new NewOrder(OrderId.of(UUID.randomUUID()), subject.next(), token.hash(), userId,
            new BuyerDetails("Ana María Pérez", "ana@example.com", "3425551234", "12345678"),
            new ShippingAddress("San Martín", "1234", Optional.of("3"), Optional.of("B"), "Santa Fe", "S3000ABC"),
            Province.SANTA_FE, LINES, OrderAmounts.of(Money.of(5_000_000), discount, Money.of(300_000)), coupon,
            PaymentProvider.MERCADOPAGO, "2026-10", NOW, StockHold.expiryOf(NOW));
    }
}
