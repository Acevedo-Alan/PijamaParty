package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.ordering.application.CheckoutCartLock.CartCoupon;
import com.pijamaparty.store.ordering.application.CheckoutCartLock.CartState;
import com.pijamaparty.store.ordering.application.SaveOrders.AppliedCoupon;
import com.pijamaparty.store.ordering.domain.BuyerDetails;
import com.pijamaparty.store.ordering.domain.CheckoutForm;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.ordering.domain.ShippingAddress;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.promotions.api.CouponStatus;
import com.pijamaparty.store.shipping.api.Province;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** UC-13, UC-14, UC-16: the buyer confirms the checkout and its cart becomes an order awaiting payment. */
class PlaceOrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");
    private static final TrackingToken TRACKING = new TrackingToken("the-new-orders-tracking-token");
    private static final Actor GUEST =
        Actor.anonymous(Optional.of(new CartToken("a-guest-cart-token")), Optional.of(TRACKING));
    private static final UUID PIJAMA = UUID.randomUUID();
    private static final UUID CAMISON = UUID.randomUUID();
    private static final OrderLine PIJAMAS =
        new OrderLine(PIJAMA, "PL-M-ROSA", "Pijama Luna", "M", "Rosa", Money.of(2_000_000), 2);
    private static final OrderLine CAMISONES =
        new OrderLine(CAMISON, "CS-S-GRIS", "Camisón Sol", "S", "Gris", Money.of(1_000_000), 1);
    private static final CheckoutForm FORM = new CheckoutForm(" Ana Pérez ", "Ana@Example.com", "342 555-1234",
        "12.345.678", "San Martín", "1234", "3", null, "Santa Fe", "s3000abc");

    private final InMemoryCheckoutCarts carts = new InMemoryCheckoutCarts();
    private final InMemoryShippingQuotes shipping = new InMemoryShippingQuotes();
    private final InMemoryReserveOrderStock stock = new InMemoryReserveOrderStock();
    private final InMemoryOrderStore orders = new InMemoryOrderStore();
    private final UUID orderId = UUID.randomUUID();
    private final PlaceOrderService placeOrder = new PlaceOrderService(carts, shipping, stock, orders, orders, carts,
        new InMemoryCurrentTerms("2026-10"), new InMemoryEnabledPaymentProviders(Set.of(PaymentProvider.MERCADOPAGO)),
        () -> orderId, Clock.fixed(NOW, ZoneOffset.UTC));

    /** 2 pijamas at ARS 20,000 and 1 camisón at ARS 10,000, 5 of each in stock; shipping to Santa Fe ARS 3,000. */
    @BeforeEach
    void arrange() {
        carts.put(GUEST, ready(Money.zero(), Optional.empty()));
        stock.stock(PIJAMA, 5);
        stock.stock(CAMISON, 5);
        shipping.rate(Province.SANTA_FE, Money.of(300_000));
    }

    @Test
    void givenAReadyCart_whenTheBuyerConfirms_thenTheOrderAwaitsPaymentWithTheServersAmountsAndTheStockReserved() {
        var result = placeOrder.execute(command(GUEST, FORM, true));

        var placed = new PlaceOrder.Result.Placed(OrderId.of(orderId), 1001, Money.of(5_300_000),
            Instant.parse("2026-10-12T15:30:00Z"));
        assertThat(result).isEqualTo(placed);
        var order = orders.placed(OrderId.of(orderId)).orElseThrow();
        assertThat(order.number()).isEqualTo(1001);
        assertThat(order.trackingTokenHash()).isEqualTo(TRACKING.hash());
        assertThat(order.userId()).isEmpty();
        assertThat(order.buyer()).isEqualTo(new BuyerDetails("Ana Pérez", "ana@example.com", "3425551234",
            "12345678"));
        assertThat(order.address()).isEqualTo(new ShippingAddress("San Martín", "1234", Optional.of("3"),
            Optional.empty(), "Santa Fe", "S3000ABC"));
        assertThat(order.province()).isEqualTo(Province.SANTA_FE);
        assertThat(order.lines()).containsExactly(PIJAMAS, CAMISONES);
        assertThat(order.amounts().subtotal()).isEqualTo(Money.of(5_000_000));
        assertThat(order.amounts().discount()).isEqualTo(Money.zero());
        assertThat(order.amounts().shipping()).isEqualTo(Money.of(300_000));
        assertThat(order.amounts().total()).isEqualTo(Money.of(5_300_000));
        assertThat(order.coupon()).isEmpty();
        assertThat(order.paymentProvider()).isEqualTo(PaymentProvider.MERCADOPAGO);
        assertThat(order.termsVersion()).isEqualTo("2026-10");
        assertThat(order.createdAt()).isEqualTo(NOW);
        assertThat(order.reservationExpiresAt()).isEqualTo(NOW.plusSeconds(30 * 60));
        assertThat(orders.statusOf(OrderId.of(orderId))).contains(OrderStatus.AWAITING_PAYMENT);
        assertThat(stock.reservedBy(OrderId.of(orderId))).containsExactly(PIJAMAS, CAMISONES);
        assertThat(stock.availableOf(PIJAMA)).isEqualTo(3);
        assertThat(carts.wasCleared(GUEST)).isTrue();
    }

    @Test
    void givenMODOSwitchedOff_whenTheBuyerChoosesIt_thenItIsRefusedAndNothingHappens() {
        var command = new PlaceOrder.Command(GUEST, UUID.randomUUID(), FORM, Province.SANTA_FE, PaymentProvider.MODO,
            true);

        assertThat(placeOrder.execute(command))
            .isEqualTo(new PlaceOrder.Result.PaymentProviderNotEnabled(PaymentProvider.MODO));
        assertNothingHappened();
    }

    @Test
    void givenMODOSwitchedOn_whenTheBuyerChoosesIt_thenTheOrderKeepsItAsItsProvider() {
        var withModo = new PlaceOrderService(carts, shipping, stock, orders, orders, carts,
            new InMemoryCurrentTerms("2026-10"),
            new InMemoryEnabledPaymentProviders(Set.of(PaymentProvider.MERCADOPAGO, PaymentProvider.MODO)),
            () -> orderId, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = withModo.execute(new PlaceOrder.Command(GUEST, UUID.randomUUID(), FORM, Province.SANTA_FE,
            PaymentProvider.MODO, true));

        assertThat(result).isInstanceOf(PlaceOrder.Result.Placed.class);
        assertThat(orders.placed(OrderId.of(orderId)).orElseThrow().paymentProvider())
            .isEqualTo(PaymentProvider.MODO);
    }

    @Test
    void givenAnApplicableCoupon_whenTheBuyerConfirms_thenTheOrderKeepsItsDiscountAndCode() {
        UUID coupon = UUID.randomUUID();
        carts.put(GUEST, ready(Money.of(500_000),
            Optional.of(new CartCoupon(coupon, "VERANO10", CouponStatus.APPLICABLE))));

        var placed = (PlaceOrder.Result.Placed) placeOrder.execute(command(GUEST, FORM, true));

        assertThat(placed.total()).isEqualTo(Money.of(4_800_000));
        var order = orders.placed(placed.orderId()).orElseThrow();
        assertThat(order.amounts().discount()).isEqualTo(Money.of(500_000));
        assertThat(order.coupon()).contains(new AppliedCoupon(coupon, "VERANO10"));
    }

    @Test
    void givenFreeShippingFromTheSubtotalAfterTheDiscount_whenTheBuyerConfirms_thenShippingIsZero() {
        shipping.freeShippingFrom(Money.of(5_000_000));

        var placed = (PlaceOrder.Result.Placed) placeOrder.execute(command(GUEST, FORM, true));

        assertThat(placed.total()).isEqualTo(Money.of(5_000_000));
    }

    @Test
    void givenACustomer_whenTheyConfirm_thenTheOrderIsTheirs() {
        UserId user = UserId.of(UUID.randomUUID());
        Actor customer = Actor.customer(user, Optional.empty(), Optional.of(TRACKING));
        carts.put(customer, ready(Money.zero(), Optional.empty()));

        placeOrder.execute(command(customer, FORM, true));

        assertThat(orders.placed(OrderId.of(orderId)).orElseThrow().userId()).contains(user);
    }

    @Test
    void givenAnEmptyRequiredFieldAndAnInvalidNationalId_whenTheBuyerConfirms_thenEachIsNamedAndNothingHappens() {
        var form = new CheckoutForm("Ana", "ana@example.com", "3425551234", "12345", "San Martín", "1234", null, null,
            " ", "3000");

        assertThat(placeOrder.execute(command(GUEST, form, true))).isEqualTo(
            new PlaceOrder.Result.InvalidBuyerData(Set.of(CheckoutForm.Field.NATIONAL_ID, CheckoutForm.Field.CITY)));
        assertNothingHappened();
    }

    @Test
    void givenTheTermsNotAccepted_whenTheBuyerConfirms_thenItIsRefusedAndNothingHappens() {
        assertThat(placeOrder.execute(command(GUEST, FORM, false))).isEqualTo(new PlaceOrder.Result.TermsNotAccepted());
        assertNothingHappened();
    }

    @Test
    void givenNoCartOrAnActorThatCannotOwnOne_whenConfirming_thenTheCartIsEmpty() {
        assertThat(placeOrder.execute(command(Actor.anonymous(Optional.empty(), Optional.of(TRACKING)), FORM, true)))
            .isEqualTo(new PlaceOrder.Result.EmptyCart());
        assertThat(placeOrder.execute(command(Actor.admin(UserId.of(UUID.randomUUID())), FORM, true)))
            .isEqualTo(new PlaceOrder.Result.EmptyCart());
        assertNothingHappened();
    }

    @Test
    void givenACartWithAVariantOutOfStockOrANewPrice_whenConfirming_thenItMustBeFixedFirst() {
        carts.put(GUEST, new CartState.NotReady());

        assertThat(placeOrder.execute(command(GUEST, FORM, true))).isEqualTo(new PlaceOrder.Result.CartNotReady());
        assertNothingHappened();
    }

    @Test
    void givenACouponThatNoLongerApplies_whenConfirming_thenItIsRefusedWithWhyAndNothingHappens() {
        carts.put(GUEST, ready(Money.zero(),
            Optional.of(new CartCoupon(UUID.randomUUID(), "VERANO10", CouponStatus.EXPIRED))));

        assertThat(placeOrder.execute(command(GUEST, FORM, true)))
            .isEqualTo(new PlaceOrder.Result.CouponNotApplicable(CouponStatus.EXPIRED));
        assertNothingHappened();
    }

    @Test
    void givenAProvinceWithoutShipping_whenConfirming_thenThereIsNoShippingAndNothingHappens() {
        assertThat(placeOrder.execute(new PlaceOrder.Command(GUEST, UUID.randomUUID(), FORM, Province.JUJUY,
            PaymentProvider.MERCADOPAGO, true))).isEqualTo(new PlaceOrder.Result.NoShipping(Province.JUJUY));
        assertNothingHappened();
    }

    @Test
    void givenTheLastUnitTakenByAnotherBuyer_whenConfirming_thenTheVariantIsNamedAndNothingIsReservedOrWritten() {
        stock.stock(CAMISON, 0);

        assertThat(placeOrder.execute(command(GUEST, FORM, true)))
            .isEqualTo(new PlaceOrder.Result.InsufficientStock(CAMISON, 0));
        assertNothingHappened();
    }

    @Test
    void givenABuyerWithoutTheNewOrdersTrackingToken_whenConfirming_thenItFailsBeforeReservingOrWriting() {
        Actor withoutToken = Actor.anonymous(Optional.of(new CartToken("another-cart-token")), Optional.empty());
        carts.put(withoutToken, ready(Money.zero(), Optional.empty()));

        assertThatThrownBy(() -> placeOrder.execute(command(withoutToken, FORM, true)))
            .isInstanceOf(IllegalStateException.class);
        assertThat(orders.placedCount()).isZero();
        assertThat(stock.availableOf(PIJAMA)).isEqualTo(5);
    }

    private void assertNothingHappened() {
        assertThat(orders.placedCount()).isZero();
        assertThat(stock.reservedBy(OrderId.of(orderId))).isEmpty();
        assertThat(stock.availableOf(PIJAMA)).isEqualTo(5);
        assertThat(carts.wasCleared(GUEST)).isFalse();
    }

    private static CartState.Ready ready(Money discount, Optional<CartCoupon> coupon) {
        return new CartState.Ready(List.of(PIJAMAS, CAMISONES), Money.of(5_000_000), discount, coupon);
    }

    private static PlaceOrder.Command command(Actor actor, CheckoutForm form, boolean acceptedTerms) {
        return new PlaceOrder.Command(actor, UUID.randomUUID(), form, Province.SANTA_FE, PaymentProvider.MERCADOPAGO,
            acceptedTerms);
    }
}
