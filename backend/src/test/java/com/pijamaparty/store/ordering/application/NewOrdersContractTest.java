package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.ordering.application.SaveOrders.AppliedCoupon;
import com.pijamaparty.store.ordering.application.SaveOrders.NewOrder;
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

/** Contract of {@link SaveOrders}, {@link OrderNumbers} and {@link OrdersToPay} (UC-14, RNF-20.6). */
public abstract class NewOrdersContractTest<T extends SaveOrders & OrderNumbers & OrdersToPay>
    extends ContractTest<T> {

    protected static final Instant NOW = Instant.parse("2026-10-12T15:00:00.123456Z");

    private static final SecureRandom RANDOM = new SecureRandom();

    /** The order as stored, read back; empty when there is none. */
    protected abstract Optional<NewOrder> storedOrder(OrderId order);

    /** Empty when there is no such order. */
    protected abstract Optional<OrderStatus> statusOf(OrderId order);

    /** The buyer that holds {@code token} is the one who inserts its order (RNF-27). */
    protected void tokenIssued(TrackingToken token) {
    }

    @Test
    void givenAGuestOrderWithACoupon_whenInserted_thenItIsStoredAwaitingPaymentAsPlaced() {
        NewOrder order = newOrder(Optional.empty(), Optional.of(new AppliedCoupon(UUID.randomUUID(), "VERANO10")),
            Optional.of("3"), Optional.of("B"));

        subject.insert(order);
        persistAndForget();

        assertThat(storedOrder(order.id())).contains(order);
        assertThat(statusOf(order.id())).contains(OrderStatus.AWAITING_PAYMENT);
    }

    @Test
    void givenAnOrderWithoutCouponFloorOrApartment_whenInserted_thenItIsStoredWithoutThem() {
        NewOrder order = newOrder(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        subject.insert(order);
        persistAndForget();

        assertThat(storedOrder(order.id())).contains(order);
    }

    @Test
    void givenACustomersOrder_whenInserted_thenItBelongsToTheCustomer() {
        NewOrder order = newOrder(Optional.of(UserId.of(UUID.randomUUID())), Optional.empty(), Optional.empty(),
            Optional.empty());

        subject.insert(order);
        persistAndForget();

        assertThat(storedOrder(order.id()).map(NewOrder::userId)).contains(order.userId());
    }

    @Test
    void givenAnOrderNumberAlreadyUsed_whenInsertingAnotherOrderWithIt_thenItFails() {
        NewOrder first = newOrder(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        subject.insert(first);
        NewOrder second = newOrder(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            first.number());

        assertThatThrownBy(() -> subject.insert(second)).isInstanceOf(RuntimeException.class);
        assertThat(storedOrder(second.id())).isEmpty();
    }

    @Test
    void givenNewOrders_whenTakingNumbers_thenEachIsNewIncreasingAndFrom1001() {
        long first = subject.next();
        long second = subject.next();

        assertThat(first).isGreaterThanOrEqualTo(1001);
        assertThat(second).isGreaterThan(first);
    }

    @Test
    void givenAPlacedOrder_whenFoundToPay_thenItHasItsNumberStatusTotalProviderBuyerAndExpiry() {
        NewOrder order = newOrder(Optional.empty(), Optional.of(new AppliedCoupon(UUID.randomUUID(), "VERANO10")),
            Optional.empty(), Optional.empty());
        subject.insert(order);
        persistAndForget();

        assertThat(subject.find(order.id())).contains(new OrdersToPay.OrderToPay(order.id(), order.number(),
            OrderStatus.AWAITING_PAYMENT, Money.of(4_800_000), PaymentProvider.MERCADOPAGO, "Ana Pérez",
            "ana@example.com", order.reservationExpiresAt()));
    }

    @Test
    void givenNoSuchOrder_whenFoundToPay_thenThereIsNone() {
        assertThat(subject.find(OrderId.of(UUID.randomUUID()))).isEmpty();
    }

    protected NewOrder newOrder(Optional<UserId> userId, Optional<AppliedCoupon> coupon, Optional<String> floor,
                                Optional<String> apartment) {
        return newOrder(userId, coupon, floor, apartment, subject.next());
    }

    private NewOrder newOrder(Optional<UserId> userId, Optional<AppliedCoupon> coupon, Optional<String> floor,
                              Optional<String> apartment, long number) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        var token = new TrackingToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        tokenIssued(token);
        var lines = List.of(
            new OrderLine(UUID.randomUUID(), "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000), 2),
            new OrderLine(UUID.randomUUID(), "CS-S-GRIS", "Camisón Sol", "S", "Gris", Money.of(1_000_000), 1));
        Money discount = coupon.isPresent() ? Money.of(500_000) : Money.zero();
        return new NewOrder(OrderId.of(UUID.randomUUID()), number, token.hash(), userId,
            new BuyerDetails("Ana", "Pérez", "ana@example.com", "3425551234", "12345678"),
            new ShippingAddress("San Martín", "1234", floor, apartment, "Santa Fe", "S3000ABC"), Province.SANTA_FE,
            lines, OrderAmounts.of(Money.of(5_000_000), discount, Money.of(300_000)), coupon,
            PaymentProvider.MERCADOPAGO, "2026-10", NOW, StockHold.expiryOf(NOW));
    }
}
