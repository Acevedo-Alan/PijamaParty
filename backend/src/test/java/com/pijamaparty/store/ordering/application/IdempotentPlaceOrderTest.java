package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.application.CheckoutCartLock.CartState;
import com.pijamaparty.store.ordering.domain.CheckoutForm;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.platform.application.InMemoryIdempotencyKeys;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.IdempotencyScope;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.shipping.api.Province;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** T-E3-14 (UC-14, RNF-20.1, RNF-20.2): the checkout places one order per {@code Idempotency-Key}. */
class IdempotentPlaceOrderTest {

    private static final Instant NOW = Instant.parse("2026-10-14T15:00:00Z");
    private static final CartToken CART = new CartToken("a-guest-cart-token");
    private static final TrackingToken FIRST_TOKEN = new TrackingToken("the-first-requests-tracking-token");
    private static final TrackingToken SECOND_TOKEN = new TrackingToken("the-second-requests-tracking-token");
    private static final Actor FIRST = Actor.anonymous(Optional.of(CART), Optional.of(FIRST_TOKEN));
    private static final Actor SECOND = Actor.anonymous(Optional.of(CART), Optional.of(SECOND_TOKEN));
    private static final IdempotencyScope SCOPE = IdempotencyScope.of(FIRST).orElseThrow();
    private static final UUID PIJAMA = UUID.randomUUID();
    private static final OrderLine PIJAMAS =
        new OrderLine(PIJAMA, "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000), 2);
    private static final CheckoutForm FORM = new CheckoutForm("Ana Pérez", "ana@example.com", "3425551234",
        "12345678", "San Martín", "1234", null, null, "Santa Fe", "S3000ABC");

    private final InMemoryCheckoutCarts carts = new InMemoryCheckoutCarts();
    private final InMemoryShippingQuotes shipping = new InMemoryShippingQuotes();
    private final InMemoryReserveOrderStock stock = new InMemoryReserveOrderStock();
    private final InMemoryOrderStore orders = new InMemoryOrderStore();
    private final InMemoryIdempotencyKeys keys = new InMemoryIdempotencyKeys();
    private final UUID orderId = UUID.randomUUID();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final IdempotentPlaceOrder placeOrder = new IdempotentPlaceOrder(
        new PlaceOrderService(carts, shipping, stock, orders, orders, carts, new InMemoryCurrentTerms("2026-10"),
            new InMemoryEnabledPaymentProviders(Set.of(PaymentProvider.MERCADOPAGO)), () -> orderId, clock),
        keys, orders, clock);
    private final UUID key = UUID.randomUUID();

    /** 2 pijamas at ARS 20,000 in the guest's cart, 5 in stock; shipping to Santa Fe ARS 3,000. */
    @BeforeEach
    void arrange() {
        carts.put(FIRST, new CartState.Ready(List.of(PIJAMAS), Money.of(4_000_000), Money.zero(), Optional.empty()));
        stock.stock(PIJAMA, 5);
        shipping.rate(Province.SANTA_FE, Money.of(300_000));
    }

    @Test
    void givenANewKey_whenTheOrderIsPlaced_thenTheKeyKeepsItsAnswerFor24Hours() {
        var placed = new PlaceOrder.Result.Placed(OrderId.of(orderId), 1001, Money.of(4_300_000),
            NOW.plus(Duration.ofMinutes(30)));

        assertThat(placeOrder.execute(command(FIRST, FORM))).isEqualTo(placed);

        assertThat(keys.expiryOf(SCOPE, key)).contains(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void givenAPlacedOrder_whenTheSameKeyArrivesAgainWithTheSameContent_thenItGetsTheSameOrderAndNothingIsRepeated() {
        var first = placeOrder.execute(command(FIRST, FORM));

        var second = placeOrder.execute(command(SECOND, FORM));

        assertThat(second).isEqualTo(first);
        assertThat(orders.placedCount()).isOne();
        assertThat(stock.availableOf(PIJAMA)).isEqualTo(3);
        assertThat(orders.linkedOrder(SECOND_TOKEN.hash())).contains(OrderId.of(orderId));
        assertThat(keys.size()).isOne();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changes")
    void givenAPlacedOrder_whenTheSameKeyArrivesWithADifferentContent_thenItIsRefusedAndNothingHappens(
        String change, UnaryOperator<PlaceOrder.Command> changed) {
        placeOrder.execute(command(FIRST, FORM));

        assertThat(placeOrder.execute(changed.apply(command(SECOND, FORM))))
            .isEqualTo(new PlaceOrder.Result.KeyReused());

        assertThat(orders.placedCount()).isOne();
        assertThat(orders.linkedOrder(SECOND_TOKEN.hash())).isEmpty();
    }

    static Stream<Object[]> changes() {
        CheckoutForm otherEmail = new CheckoutForm("Ana Pérez", "otra@example.com", "3425551234", "12345678",
            "San Martín", "1234", null, null, "Santa Fe", "S3000ABC");
        CheckoutForm emptyFloor = new CheckoutForm("Ana Pérez", "ana@example.com", "3425551234", "12345678",
            "San Martín", "1234", "", null, "Santa Fe", "S3000ABC");
        CheckoutForm shiftedText = new CheckoutForm("Ana Pérez", "ana@example.com", "3425551234", "12345678",
            "San Martín", "123", "4", null, "Santa Fe", "S3000ABC");
        return Stream.of(
            new Object[] {"another email", (UnaryOperator<PlaceOrder.Command>) c -> withForm(c, otherEmail)},
            new Object[] {"an empty floor instead of none", (UnaryOperator<PlaceOrder.Command>) c ->
                withForm(c, emptyFloor)},
            new Object[] {"text moved from one field to the next", (UnaryOperator<PlaceOrder.Command>) c ->
                withForm(c, shiftedText)},
            new Object[] {"another province", (UnaryOperator<PlaceOrder.Command>) c -> new PlaceOrder.Command(
                c.actor(), c.idempotencyKey(), c.form(), Province.CORDOBA, c.paymentProvider(), c.acceptedTerms())},
            new Object[] {"the terms not accepted", (UnaryOperator<PlaceOrder.Command>) c -> new PlaceOrder.Command(
                c.actor(), c.idempotencyKey(), c.form(), c.province(), c.paymentProvider(), false)});
    }

    @Test
    void givenARequestWithTheKeyStillInFlight_whenTheSameKeyArrives_thenItIsToldToTryAgainAndNothingHappens() {
        keys.holdElsewhere(SCOPE, key);

        assertThat(placeOrder.execute(command(FIRST, FORM))).isEqualTo(new PlaceOrder.Result.KeyInUse());

        assertThat(orders.placedCount()).isZero();
        assertThat(stock.availableOf(PIJAMA)).isEqualTo(5);
    }

    @Test
    void givenARefusedCheckout_whenTheSameKeyArrivesAgain_thenItRunsAgainSinceNothingWasStored() {
        assertThat(placeOrder.execute(new PlaceOrder.Command(FIRST, key, FORM, Province.SANTA_FE,
            PaymentProvider.MERCADOPAGO, false))).isEqualTo(new PlaceOrder.Result.TermsNotAccepted());
        assertThat(keys.size()).isZero();

        assertThat(placeOrder.execute(command(FIRST, FORM))).isInstanceOf(PlaceOrder.Result.Placed.class);
    }

    @Test
    void givenTheSameKeyInAnotherCart_whenItArrives_thenItIsAnotherKey() {
        placeOrder.execute(command(FIRST, FORM));
        Actor otherGuest = Actor.anonymous(Optional.of(new CartToken("another-cart-token")), Optional.of(SECOND_TOKEN));

        assertThat(placeOrder.execute(command(otherGuest, FORM))).isEqualTo(new PlaceOrder.Result.EmptyCart());
        assertThat(orders.linkedOrder(SECOND_TOKEN.hash())).isEmpty();
    }

    @Test
    void givenACustomer_whenTheSameKeyArrivesAgainFromAnotherCart_thenItGetsItsFirstOrder() {
        UserId user = UserId.of(UUID.randomUUID());
        Actor customer = Actor.customer(user, Optional.of(CART), Optional.of(FIRST_TOKEN));
        Actor sameCustomerElsewhere = Actor.customer(user, Optional.empty(), Optional.of(SECOND_TOKEN));
        carts.put(customer, new CartState.Ready(List.of(PIJAMAS), Money.of(4_000_000), Money.zero(),
            Optional.empty()));
        var first = placeOrder.execute(command(customer, FORM));

        assertThat(placeOrder.execute(command(sameCustomerElsewhere, FORM))).isEqualTo(first);
        assertThat(orders.placedCount()).isOne();
    }

    @Test
    void givenStaffWithoutACart_whenConfirming_thenThereIsNothingToPlaceOrToStore() {
        assertThat(placeOrder.execute(command(Actor.admin(UserId.of(UUID.randomUUID())), FORM)))
            .isEqualTo(new PlaceOrder.Result.EmptyCart());
        assertThat(keys.size()).isZero();
    }

    @Test
    void givenTheSameContent_whenHashingItTwice_thenTheHashIsTheSameSha256() {
        String hash = IdempotentPlaceOrder.requestHashOf(command(FIRST, FORM));

        assertThat(IdempotentPlaceOrder.requestHashOf(command(SECOND, FORM))).isEqualTo(hash).matches("[0-9a-f]{64}");
    }

    private PlaceOrder.Command command(Actor actor, CheckoutForm form) {
        return new PlaceOrder.Command(actor, key, form, Province.SANTA_FE, PaymentProvider.MERCADOPAGO, true);
    }

    private static PlaceOrder.Command withForm(PlaceOrder.Command command, CheckoutForm form) {
        return new PlaceOrder.Command(command.actor(), command.idempotencyKey(), form, command.province(),
            command.paymentProvider(), command.acceptedTerms());
    }
}
