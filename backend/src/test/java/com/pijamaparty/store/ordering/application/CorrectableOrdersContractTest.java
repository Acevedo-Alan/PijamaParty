package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.api.PaidOrders.PaidOrder;
import com.pijamaparty.store.ordering.application.CorrectableOrders.CorrectableOrder;
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
 * Contract of {@link CorrectableOrders} and {@link BuyerEmails} (UC-17, T-E3-23): the order of a tracking link, the
 * checkout's or an email's, found by its token to correct its email; replacing the email changes nothing else.
 */
public abstract class CorrectableOrdersContractTest<T extends SaveOrders & OrderNumbers & CorrectableOrders
    & BuyerEmails & PaidOrderDetails & SaveTrackingLinks> extends ContractTest<T> {

    protected static final Instant NOW = Instant.parse("2026-10-13T15:00:00.123456Z");

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final List<OrderLine> LINES = List.of(
        new OrderLine(UUID.randomUUID(), "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000), 2));

    /** The buyer that holds {@code token} is the one who inserts its order (RNF-27). */
    protected void tokenIssued(TrackingToken token) {
    }

    /** The order went through UC-18 up to {@code SHIPPED}. */
    protected abstract void shipped(OrderId order);

    @Test
    void givenAPlacedOrder_whenLockedByItsToken_thenItComesWithItsNumberAwaitingPayment() {
        TrackingToken token = newToken();
        NewOrder order = newOrder(token);
        subject.insert(order);
        persistAndForget();

        assertThat(subject.lock(token))
            .contains(new CorrectableOrder(order.id(), order.number(), OrderStatus.AWAITING_PAYMENT));
    }

    @Test
    void givenAShippedOrder_whenLockedByItsToken_thenItComesShipped() {
        TrackingToken token = newToken();
        NewOrder order = newOrder(token);
        subject.insert(order);
        shipped(order.id());
        persistAndForget();

        assertThat(subject.lock(token)).map(CorrectableOrder::status).contains(OrderStatus.SHIPPED);
    }

    @Test
    void givenTheLinkOfAConfirmationEmail_whenLockedByIt_thenItIsTheOrderOfThatEmail() {
        NewOrder order = newOrder(newToken());
        subject.insert(order);
        TrackingToken emailed = newToken();
        subject.add(order.id(), emailed.hash(), NOW);
        persistAndForget();

        assertThat(subject.lock(emailed)).map(CorrectableOrder::id).contains(order.id());
    }

    @Test
    void givenTwoOrders_whenLockedByTheTokenOfOne_thenOnlyThatOneComes() {
        NewOrder first = newOrder(newToken());
        TrackingToken token = newToken();
        NewOrder second = newOrder(token);
        subject.insert(first);
        subject.insert(second);
        persistAndForget();

        assertThat(subject.lock(token)).map(CorrectableOrder::id).contains(second.id());
    }

    @Test
    void givenATokenOfNoOrder_whenLocked_thenThereIsNone() {
        assertThat(subject.lock(newToken())).isEmpty();
    }

    @Test
    void givenAPlacedOrder_whenItsEmailIsReplaced_thenOnlyTheEmailChanges() {
        NewOrder order = newOrder(newToken());
        subject.insert(order);
        PaidOrder before = subject.detailsOf(order.id()).orElseThrow();

        assertThat(subject.replace(order.id(), "ana.perez@gmail.com", NOW.plusSeconds(60))).isTrue();
        persistAndForget();

        assertThat(subject.detailsOf(order.id())).contains(new PaidOrder(before.orderId(), before.number(),
            before.buyerFirstName(), before.buyerLastName(), "ana.perez@gmail.com", before.lines(), before.subtotal(),
            before.discount(), before.couponCode(), before.shipping(), before.total(), before.address()));
    }

    @Test
    void givenNoSuchOrder_whenItsEmailIsReplaced_thenNothingIsReplaced() {
        assertThat(subject.replace(OrderId.of(UUID.randomUUID()), "ana.perez@gmail.com", NOW)).isFalse();
    }

    protected TrackingToken newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        var token = new TrackingToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        tokenIssued(token);
        return token;
    }

    private NewOrder newOrder(TrackingToken token) {
        return new NewOrder(OrderId.of(UUID.randomUUID()), subject.next(), token.hash(), Optional.empty(),
            new BuyerDetails("Ana María", "Pérez", "ana.perez@gmial.com", "3425551234", "12345678"),
            new ShippingAddress("San Martín", "1234", Optional.empty(), Optional.empty(), "Santa Fe", "S3000ABC"),
            Province.SANTA_FE, LINES, OrderAmounts.of(Money.of(4_000_000), Money.zero(), Money.of(300_000)),
            Optional.empty(), PaymentProvider.MERCADOPAGO, "2026-10", NOW, StockHold.expiryOf(NOW));
    }
}
