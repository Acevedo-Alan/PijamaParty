package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.application.CheckoutCartLock.CartCoupon;
import com.pijamaparty.store.ordering.application.CheckoutCartLock.CartState;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.promotions.api.CouponStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The arrangement prices each cart as the cart module would, and hands the fake the resulting state. */
class InMemoryCheckoutCartsContractTest extends CheckoutCartsContractTest<InMemoryCheckoutCarts> {

    private final Map<Actor, List<GivenLine>> lines = new HashMap<>();
    private final Map<Actor, GivenCoupon> coupons = new HashMap<>();

    @Override
    protected InMemoryCheckoutCarts createSubject() {
        return new InMemoryCheckoutCarts();
    }

    @Override
    protected UUID givenLine(Actor guest, long priceCents, long seenPriceCents, int quantity, int available) {
        UUID variant = UUID.randomUUID();
        lines.computeIfAbsent(guest, key -> new ArrayList<>())
            .add(new GivenLine(variant, priceCents, seenPriceCents, quantity, available));
        price(guest);
        return variant;
    }

    @Override
    protected UUID givenCoupon(Actor guest, String code, int percent, boolean active) {
        UUID coupon = UUID.randomUUID();
        coupons.put(guest, new GivenCoupon(coupon, code, percent, active));
        price(guest);
        return coupon;
    }

    private void price(Actor guest) {
        List<GivenLine> given = lines.getOrDefault(guest, List.of());
        boolean ready = given.stream()
            .allMatch(line -> line.quantity() <= line.available() && line.priceCents() == line.seenPriceCents());
        if (!ready) {
            subject.put(guest, new CartState.NotReady());
            return;
        }
        List<OrderLine> orderLines = given.stream()
            .map(line -> new OrderLine(line.variant(), skuOf(line.variant()), "Pijama", "M", "Rosa",
                Money.of(line.priceCents()), line.quantity()))
            .toList();
        Money subtotal = orderLines.stream().map(OrderLine::amount).reduce(Money.zero(), Money::plus);
        Optional<CartCoupon> coupon = Optional.ofNullable(coupons.get(guest)).map(c ->
            new CartCoupon(c.id(), c.code(), c.active() ? CouponStatus.APPLICABLE : CouponStatus.INACTIVE));
        Money discount = Optional.ofNullable(coupons.get(guest)).filter(GivenCoupon::active)
            .map(c -> subtotal.percent(c.percent())).orElse(Money.zero());
        subject.put(guest, new CartState.Ready(orderLines, subtotal, discount, coupon));
    }

    private record GivenLine(UUID variant, long priceCents, long seenPriceCents, int quantity, int available) {}

    private record GivenCoupon(UUID id, String code, int percent, boolean active) {}
}
