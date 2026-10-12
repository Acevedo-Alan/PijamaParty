package com.pijamaparty.store.cart.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartId;
import com.pijamaparty.store.cart.domain.CartOwner;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T-E2-04: {@link RemoveIdleCarts}. */
class RemoveIdleCartsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T07:00:00Z");
    private static final CartOwner GUEST = new CartOwner.Guest(new CartToken("a-guest-cart-token").hash());
    private static final CartOwner OTHER_GUEST = new CartOwner.Guest(new CartToken("another-token").hash());
    private static final CartOwner CUSTOMER = new CartOwner.Customer(UserId.of(UUID.randomUUID()));

    private final InMemoryCartStore carts = new InMemoryCartStore();
    private final RemoveIdleCartsService removeIdleCarts =
        new RemoveIdleCartsService(carts, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void givenCartsIdleForMoreThan30Days_whenTheCleanupRuns_thenTheyAreDeletedAndTheOthersKept() {
        Duration thirtyDays = Duration.ofDays(30);
        carts.save(Cart.open(CartId.of(UUID.randomUUID()), GUEST, NOW.minus(thirtyDays).minusSeconds(1)));
        carts.save(Cart.open(CartId.of(UUID.randomUUID()), CUSTOMER, NOW.minus(Duration.ofDays(90))));
        carts.save(Cart.open(CartId.of(UUID.randomUUID()), OTHER_GUEST, NOW.minus(thirtyDays)));

        assertThat(removeIdleCarts.execute()).isEqualTo(new RemoveIdleCarts.Result(2));

        assertThat(carts.findByOwner(GUEST)).isEmpty();
        assertThat(carts.findByOwner(CUSTOMER)).isEmpty();
        assertThat(carts.findByOwner(OTHER_GUEST)).isPresent();
    }

    @Test
    void givenNoIdleCart_whenTheCleanupRuns_thenNothingIsDeleted() {
        carts.save(Cart.open(CartId.of(UUID.randomUUID()), GUEST, NOW.minus(Duration.ofDays(1))));

        assertThat(removeIdleCarts.execute()).isEqualTo(new RemoveIdleCarts.Result(0));
        assertThat(carts.size()).isEqualTo(1);
    }
}
