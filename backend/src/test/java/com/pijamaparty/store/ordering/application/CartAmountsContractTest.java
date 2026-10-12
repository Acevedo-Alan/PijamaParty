package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link CartAmounts} (UC-09, UC-13). */
public abstract class CartAmountsContractTest<T extends CartAmounts> extends ContractTest<T> {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * The cart of {@code guest} holds one line of {@code quantity} units of a variant sold at {@code unitPriceCents}
     * with {@code available} units left (0: sold out, otherwise at least {@code quantity}), and a coupon of
     * {@code couponPercent} percent when present.
     */
    protected abstract void givenGuestCart(Actor guest, long unitPriceCents, int quantity, int available,
                                           OptionalInt couponPercent);

    @Test
    void givenACartWithA10PercentCoupon_whenAskedForItsAmounts_thenItHasTheSubtotalAndTheDiscount() {
        Actor guest = newGuest();
        givenGuestCart(guest, 2_000_000, 2, 5, OptionalInt.of(10));

        assertThat(subject.of(guest)).contains(new CartAmounts.Amounts(Money.of(4_000_000), Money.of(400_000)));
    }

    @Test
    void givenACartWhoseOnlyLineIsSoldOut_whenAskedForItsAmounts_thenThereIsNothingToSell() {
        Actor guest = newGuest();
        givenGuestCart(guest, 2_000_000, 1, 0, OptionalInt.empty());

        assertThat(subject.of(guest)).isEmpty();
    }

    @Test
    void givenTheCartOfAnotherGuest_whenAnActorWithoutOneAsks_thenThereIsNothingToSell() {
        givenGuestCart(newGuest(), 2_000_000, 1, 5, OptionalInt.empty());

        assertThat(subject.of(newGuest())).isEmpty();
        assertThat(subject.of(Actor.admin(UserId.of(UUID.randomUUID())))).isEmpty();
    }

    /** A guest with a cart token as the server issues them: 32 random bytes in unpadded base64url. */
    protected static Actor newGuest() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        var token = new CartToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        return Actor.anonymous(Optional.of(token), Optional.empty());
    }
}
