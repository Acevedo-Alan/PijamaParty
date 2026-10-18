package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.application.CheckoutCartLock.CartCoupon;
import com.pijamaparty.store.ordering.application.CheckoutCartLock.CartDiscount;
import com.pijamaparty.store.ordering.application.CheckoutCartLock.CartState;
import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.promotions.api.CouponStatus;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link CheckoutCartLock} and {@link ClearCart} over the same carts (UC-09, UC-10, UC-13). */
public abstract class CheckoutCartsContractTest<T extends CheckoutCartLock & ClearCart> extends ContractTest<T> {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Adds to the cart of {@code guest} (created with its first line) {@code quantity} units of a new variant of
     * "Pijama", size "M", color "Rosa", SKU {@link #skuOf}, sold at {@code priceCents} with {@code available} units
     * left; {@code seenPriceCents} is the price the guest last saw. Returns the variant.
     */
    protected abstract UUID givenLine(Actor guest, long priceCents, long seenPriceCents, int quantity, int available);

    /** The cart of {@code guest} holds a coupon {@code code} of {@code percent} percent, active or not. */
    protected abstract UUID givenCoupon(Actor guest, String code, int percent, boolean active);

    /** UC-23: an active automatic coupon of {@code percent} percent of the whole cart, without a minimum. */
    protected abstract UUID givenAutomaticCoupon(String code, int percent);

    /** The SKU of the variants of {@link #givenLine}. */
    protected static String skuOf(UUID variantId) {
        return "PP-" + variantId.toString().substring(0, 8);
    }

    @Test
    void givenAvailableItemsAndAnApplicableCoupon_whenLocked_thenTheCartIsReadyWithItsLinesInOrder() {
        Actor guest = newGuest();
        UUID first = givenLine(guest, 2_000_000, 2_000_000, 2, 5);
        UUID second = givenLine(guest, 1_000_000, 1_000_000, 1, 1);
        UUID coupon = givenCoupon(guest, "VERANO10", 10, true);

        assertThat(subject.lock(guest)).isEqualTo(new CartState.Ready(
            List.of(new OrderLine(first, skuOf(first), "Pijama", "M", "Rosa", Money.of(2_000_000), 2),
                new OrderLine(second, skuOf(second), "Pijama", "M", "Rosa", Money.of(1_000_000), 1)),
            Money.of(5_000_000), Optional.of(new CartCoupon(coupon, "VERANO10", CouponStatus.APPLICABLE)),
            Optional.of(new CartDiscount(coupon, "VERANO10", Money.of(500_000)))));
    }

    @Test
    void givenACouponThatNoLongerApplies_whenLocked_thenItComesWithItsStatusAndNoDiscount() {
        Actor guest = newGuest();
        givenLine(guest, 2_000_000, 2_000_000, 1, 5);
        UUID coupon = givenCoupon(guest, "INVIERNO", 10, false);

        var ready = (CartState.Ready) subject.lock(guest);

        assertThat(ready.discount()).isEmpty();
        assertThat(ready.coupon()).contains(new CartCoupon(coupon, "INVIERNO", CouponStatus.INACTIVE));
    }

    @Test
    void givenAnAutomaticCouponThatDiscountsMoreThanTheHeldCode_whenLocked_thenItIsTheCartsDiscount() {
        Actor guest = newGuest();
        givenLine(guest, 2_000_000, 2_000_000, 1, 5);
        UUID coupon = givenCoupon(guest, "VERANO10", 10, true);
        UUID automatic = givenAutomaticCoupon("OTONO20", 20);

        var ready = (CartState.Ready) subject.lock(guest);

        assertThat(ready.coupon()).contains(new CartCoupon(coupon, "VERANO10", CouponStatus.APPLICABLE));
        assertThat(ready.discount()).contains(new CartDiscount(automatic, "OTONO20", Money.of(400_000)));
        assertThat(ready.discountAmount()).isEqualTo(Money.of(400_000));
    }

    @Test
    void givenAnItemSoldOutOrWithAnUnacknowledgedPrice_whenLocked_thenTheCartIsNotReady() {
        Actor soldOut = newGuest();
        givenLine(soldOut, 2_000_000, 2_000_000, 1, 5);
        givenLine(soldOut, 1_000_000, 1_000_000, 1, 0);
        Actor newPrice = newGuest();
        givenLine(newPrice, 2_200_000, 2_000_000, 1, 5);

        assertThat(subject.lock(soldOut)).isEqualTo(new CartState.NotReady());
        assertThat(subject.lock(newPrice)).isEqualTo(new CartState.NotReady());
    }

    @Test
    void givenNoCartOrAnActorThatCannotOwnOne_whenLocked_thenTheCartIsEmpty() {
        givenLine(newGuest(), 2_000_000, 2_000_000, 1, 5);

        assertThat(subject.lock(newGuest())).isEqualTo(new CartState.Empty());
        assertThat(subject.lock(Actor.admin(UserId.of(UUID.randomUUID())))).isEqualTo(new CartState.Empty());
    }

    @Test
    void givenACartWithItemsAndACoupon_whenCleared_thenItIsEmptyAndOtherCartsAreLeftAlone() {
        Actor guest = newGuest();
        givenLine(guest, 2_000_000, 2_000_000, 1, 5);
        givenCoupon(guest, "VERANO10", 10, true);
        Actor other = newGuest();
        givenLine(other, 2_000_000, 2_000_000, 1, 5);

        subject.clear(guest);
        subject.clear(newGuest());
        persistAndForget();

        assertThat(subject.lock(guest)).isEqualTo(new CartState.Empty());
        assertThat(subject.lock(other)).isInstanceOf(CartState.Ready.class);
    }

    /** A guest with a cart token as the server issues them: 32 random bytes in unpadded base64url. */
    protected static Actor newGuest() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        var token = new CartToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        return Actor.anonymous(Optional.of(token), Optional.empty());
    }
}
