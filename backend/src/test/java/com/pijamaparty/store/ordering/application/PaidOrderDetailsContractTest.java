package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.api.PaidOrders.Address;
import com.pijamaparty.store.ordering.api.PaidOrders.Line;
import com.pijamaparty.store.ordering.api.PaidOrders.PaidOrder;
import com.pijamaparty.store.ordering.application.SaveOrders.AppliedCoupon;
import com.pijamaparty.store.ordering.application.SaveOrders.NewOrder;
import com.pijamaparty.store.ordering.application.TrackedOrders.TrackedOrder;
import com.pijamaparty.store.ordering.domain.BuyerDetails;
import com.pijamaparty.store.ordering.domain.OrderAmounts;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.ordering.domain.ShippingAddress;
import com.pijamaparty.store.ordering.domain.StockHold;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.shipping.api.Province;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link PaidOrderDetails} and {@link SaveTrackingLinks} (UC-17, T-E3-09): what the payment-confirmed
 * emails tell about an order, and the extra tracking links they carry, which open the order as the checkout's does.
 */
public abstract class PaidOrderDetailsContractTest<
    T extends SaveOrders & OrderNumbers & TrackedOrders & PaidOrderDetails & SaveTrackingLinks>
    extends ContractTest<T> {

    protected static final Instant NOW = Instant.parse("2026-10-13T15:00:00.123456Z");

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final List<OrderLine> LINES = List.of(
        new OrderLine(UUID.randomUUID(), "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000), 2),
        new OrderLine(UUID.randomUUID(), "CS-S-GRIS", "Camisón Sol", "S", "Gris", Money.of(1_000_000), 1));

    private final Map<String, TrackingToken> checkoutTokens = new HashMap<>();

    /** The buyer that holds {@code token} is the one who inserts its order (RNF-27). */
    protected void tokenIssued(TrackingToken token) {
    }

    @Test
    void givenAPlacedOrderWithACoupon_whenItsDetailsAreRead_thenTheyAreAsTheBuyerPlacedIt() {
        NewOrder order = newOrder(Optional.of(new AppliedCoupon(UUID.randomUUID(), "VERANO10")),
            Optional.of("3"), Optional.of("B"));
        subject.insert(order);
        persistAndForget();

        assertThat(subject.detailsOf(order.id())).contains(new PaidOrder(order.id().value(), order.number(),
            "Ana María Pérez", "ana@example.com",
            List.of(new Line("Pijama Luna", "M", "Rosa", 2, Money.of(2_000_000), Money.of(4_000_000)),
                new Line("Camisón Sol", "S", "Gris", 1, Money.of(1_000_000), Money.of(1_000_000))),
            Money.of(5_000_000), Money.of(500_000), Optional.of("VERANO10"), Money.of(300_000), Money.of(4_800_000),
            new Address("San Martín", "1234", Optional.of("3"), Optional.of("B"), "Santa Fe", "SANTA_FE",
                "S3000ABC")));
    }

    @Test
    void givenAnOrderWithoutCouponFloorOrApartment_whenItsDetailsAreRead_thenThoseAreEmpty() {
        NewOrder order = newOrder(Optional.empty(), Optional.empty(), Optional.empty());
        subject.insert(order);
        persistAndForget();

        assertThat(subject.detailsOf(order.id())).hasValueSatisfying(details -> {
            assertThat(details.couponCode()).isEmpty();
            assertThat(details.discount()).isEqualTo(Money.zero());
            assertThat(details.address().floor()).isEmpty();
            assertThat(details.address().apartment()).isEmpty();
        });
    }

    @Test
    void givenNoSuchOrder_whenItsDetailsAreRead_thenThereAreNone() {
        assertThat(subject.detailsOf(OrderId.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void givenALinkAddedToAnOrder_whenFoundByItsToken_thenItIsThatOrderAndTheCheckoutsTokenStillWorks() {
        NewOrder order = newOrder(Optional.empty(), Optional.empty(), Optional.empty());
        subject.insert(order);
        TrackingToken emailed = newToken();

        assertThat(subject.add(order.id(), emailed.hash(), NOW)).isTrue();
        persistAndForget();

        assertThat(subject.find(emailed).map(TrackedOrder::id)).contains(order.id());
        assertThat(subject.find(checkoutTokenOf(order)).map(TrackedOrder::id)).contains(order.id());
    }

    @Test
    void givenTwoLinksAddedToAnOrder_whenFoundByEither_thenBothOpenIt() {
        NewOrder order = newOrder(Optional.empty(), Optional.empty(), Optional.empty());
        subject.insert(order);
        TrackingToken first = newToken();
        TrackingToken second = newToken();

        subject.add(order.id(), first.hash(), NOW);
        subject.add(order.id(), second.hash(), NOW);
        persistAndForget();

        assertThat(subject.find(first).map(TrackedOrder::id)).contains(order.id());
        assertThat(subject.find(second).map(TrackedOrder::id)).contains(order.id());
    }

    @Test
    void givenALinkOfAnotherOrder_whenFoundByIt_thenOnlyThatOtherOrderIsAnswered() {
        NewOrder mine = newOrder(Optional.empty(), Optional.empty(), Optional.empty());
        NewOrder other = newOrder(Optional.empty(), Optional.empty(), Optional.empty());
        subject.insert(mine);
        subject.insert(other);
        TrackingToken othersLink = newToken();
        subject.add(other.id(), othersLink.hash(), NOW);
        persistAndForget();

        assertThat(subject.find(othersLink).map(TrackedOrder::id)).contains(other.id());
    }

    @Test
    void givenNoSuchOrder_whenALinkIsAdded_thenNothingIsAdded() {
        TrackingToken token = newToken();

        assertThat(subject.add(OrderId.of(UUID.randomUUID()), token.hash(), NOW)).isFalse();
        persistAndForget();

        assertThat(subject.find(token)).isEmpty();
    }

    protected TrackingToken newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        var token = new TrackingToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        tokenIssued(token);
        return token;
    }

    private TrackingToken checkoutTokenOf(NewOrder order) {
        return checkoutTokens.get(order.trackingTokenHash());
    }

    private NewOrder newOrder(Optional<AppliedCoupon> coupon, Optional<String> floor, Optional<String> apartment) {
        TrackingToken token = newToken();
        checkoutTokens.put(token.hash(), token);
        Money discount = coupon.isPresent() ? Money.of(500_000) : Money.zero();
        return new NewOrder(OrderId.of(UUID.randomUUID()), subject.next(), token.hash(), Optional.empty(),
            new BuyerDetails("Ana María Pérez", "ana@example.com", "3425551234", "12345678"),
            new ShippingAddress("San Martín", "1234", floor, apartment, "Santa Fe", "S3000ABC"),
            Province.SANTA_FE, LINES, OrderAmounts.of(Money.of(5_000_000), discount, Money.of(300_000)), coupon,
            PaymentProvider.MERCADOPAGO, "2026-10", NOW, StockHold.expiryOf(NOW));
    }
}
