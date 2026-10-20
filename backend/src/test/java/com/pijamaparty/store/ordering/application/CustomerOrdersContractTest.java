package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.application.CustomerOrderViews.CustomerOrder;
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

/**
 * Contract of {@link CustomerOrderPages} and {@link CustomerOrderViews} (UC-22, T-E4-07): a customer's orders, and
 * only its own.
 */
public abstract class CustomerOrdersContractTest<
    T extends SaveOrders & OrderNumbers & CustomerOrderPages & CustomerOrderViews> extends ContractTest<T> {

    protected static final Instant NOW = Instant.parse("2026-10-13T15:00:00.123456Z");

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final List<OrderLine> LINES = List.of(
        new OrderLine(UUID.randomUUID(), "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000), 2),
        new OrderLine(UUID.randomUUID(), "CS-S-GRIS", "Camisón Sol", "S", "Gris", Money.of(1_000_000), 1));

    private static final ShippingAddress ADDRESS = new ShippingAddress("San Martín", "1234", Optional.of("3"),
        Optional.of("B"), "Santa Fe", "S3000ABC");

    protected final UserId ana = UserId.of(UUID.randomUUID());
    protected final UserId bruno = UserId.of(UUID.randomUUID());

    /** The buyer that holds {@code token} is the one who inserts its order (RNF-27). */
    protected void tokenIssued(TrackingToken token) {
    }

    /** The order went through UC-18 up to {@code SHIPPED} with this shipment tracking number. */
    protected abstract void shipped(OrderId order, String shipmentTrackingNumber);

    @Test
    void givenACustomerWithTwoOrders_whenListed_thenBothAreAnsweredNewestFirst() {
        NewOrder older = placed(Optional.of(ana), NOW, Optional.empty());
        NewOrder newer = placed(Optional.of(ana), NOW.plusSeconds(3_600), Optional.empty());
        persistAndForget();

        assertThat(subject.page(ana, 0, 20)).containsExactly(
            new CustomerOrderSummary(newer.id(), newer.number(), newer.createdAt(), OrderStatus.AWAITING_PAYMENT,
                newer.amounts().total()),
            new CustomerOrderSummary(older.id(), older.number(), older.createdAt(), OrderStatus.AWAITING_PAYMENT,
                older.amounts().total()));
    }

    @Test
    void givenOrdersOfTheSameInstant_whenListed_thenTheHighestNumberComesFirst() {
        NewOrder first = placed(Optional.of(ana), NOW, Optional.empty());
        NewOrder second = placed(Optional.of(ana), NOW, Optional.empty());
        persistAndForget();

        assertThat(subject.page(ana, 0, 20)).extracting(CustomerOrderSummary::id)
            .containsExactly(second.id(), first.id());
    }

    @Test
    void givenOrdersOfAnotherCustomerAndOfAGuest_whenACustomerIsListed_thenOnlyItsOwnAreAnswered() {
        NewOrder anas = placed(Optional.of(ana), NOW, Optional.empty());
        placed(Optional.of(bruno), NOW.plusSeconds(60), Optional.empty());
        placed(Optional.empty(), NOW.plusSeconds(120), Optional.empty());
        persistAndForget();

        assertThat(subject.page(ana, 0, 20)).extracting(CustomerOrderSummary::id).containsExactly(anas.id());
    }

    @Test
    void givenThreeOrders_whenListedFromAnOffset_thenTheLimitIsKept() {
        placed(Optional.of(ana), NOW, Optional.empty());
        NewOrder middle = placed(Optional.of(ana), NOW.plusSeconds(60), Optional.empty());
        placed(Optional.of(ana), NOW.plusSeconds(120), Optional.empty());
        persistAndForget();

        assertThat(subject.page(ana, 1, 1)).extracting(CustomerOrderSummary::id).containsExactly(middle.id());
    }

    @Test
    void givenACustomerWithoutOrders_whenListed_thenNoneIsAnswered() {
        assertThat(subject.page(ana, 0, 20)).isEmpty();
    }

    @Test
    void givenACustomersOrderWithACoupon_whenFound_thenItIsAsPlacedWithItsFullAddress() {
        NewOrder order = placed(Optional.of(ana), NOW,
            Optional.of(new AppliedCoupon(UUID.randomUUID(), "VERANO10")));
        persistAndForget();

        assertThat(subject.find(ana, order.id())).contains(new CustomerOrder(order.id(), order.number(),
            OrderStatus.AWAITING_PAYMENT, NOW, LINES,
            OrderAmounts.of(Money.of(5_000_000), Money.of(500_000), Money.of(300_000)), Optional.of("VERANO10"),
            ADDRESS, Province.SANTA_FE, Optional.empty()));
    }

    @Test
    void givenAShippedOrder_whenFound_thenItHasItsShipmentTrackingNumber() {
        NewOrder order = placed(Optional.of(ana), NOW, Optional.empty());
        shipped(order.id(), "CA123456789AR");
        persistAndForget();

        assertThat(subject.find(ana, order.id())).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(found.shipmentTrackingNumber()).contains("CA123456789AR");
            assertThat(found.couponCode()).isEmpty();
        });
    }

    @Test
    void givenAnotherCustomersOrder_whenFound_thenThereIsNone() {
        NewOrder brunos = placed(Optional.of(bruno), NOW, Optional.empty());
        persistAndForget();

        assertThat(subject.find(ana, brunos.id())).isEmpty();
    }

    @Test
    void givenAGuestOrder_whenFoundByACustomer_thenThereIsNone() {
        NewOrder guests = placed(Optional.empty(), NOW, Optional.empty());
        persistAndForget();

        assertThat(subject.find(ana, guests.id())).isEmpty();
    }

    @Test
    void givenAnUnknownOrder_whenFound_thenThereIsNone() {
        assertThat(subject.find(ana, OrderId.of(UUID.randomUUID()))).isEmpty();
    }

    protected TrackingToken newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        var token = new TrackingToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        tokenIssued(token);
        return token;
    }

    protected NewOrder placed(Optional<UserId> customer, Instant at, Optional<AppliedCoupon> coupon) {
        Money discount = coupon.isPresent() ? Money.of(500_000) : Money.zero();
        var order = new NewOrder(OrderId.of(UUID.randomUUID()), subject.next(), newToken().hash(), customer,
            new BuyerDetails("Ana María", "Pérez", "ana@example.com", "3425551234", "12345678"), ADDRESS,
            Province.SANTA_FE, LINES, OrderAmounts.of(Money.of(5_000_000), discount, Money.of(300_000)), coupon,
            PaymentProvider.MERCADOPAGO, "2026-10", at, StockHold.expiryOf(at));
        subject.insert(order);
        return order;
    }
}
