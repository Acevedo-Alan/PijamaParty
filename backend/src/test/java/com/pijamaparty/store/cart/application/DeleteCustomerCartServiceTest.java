package com.pijamaparty.store.cart.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartId;
import com.pijamaparty.store.cart.domain.CartOwner;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-28 (T-E4-16): a deleted customer's cart goes with its account, and only that cart. */
class DeleteCustomerCartServiceTest {

    private static final Instant OPENED = Instant.parse("2026-10-17T15:00:00Z");
    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final UserId BRUNO = UserId.of(UUID.randomUUID());

    private final InMemoryCartStore carts = new InMemoryCartStore();
    private final DeleteCustomerCartService delete = new DeleteCustomerCartService(carts, carts);

    @Test
    void givenTheCustomersCartAndAnotherOne_whenDeleting_thenOnlyItsOwnIsGone() {
        carts.save(Cart.open(CartId.of(UUID.randomUUID()), new CartOwner.Customer(ANA), OPENED));
        carts.save(Cart.open(CartId.of(UUID.randomUUID()), new CartOwner.Customer(BRUNO), OPENED));

        var result = delete.execute(new DeleteCustomerCart.Command(ANA));

        assertThat(result).isEqualTo(new DeleteCustomerCart.Result.Deleted());
        assertThat(carts.findByOwner(new CartOwner.Customer(ANA))).isEmpty();
        assertThat(carts.findByOwner(new CartOwner.Customer(BRUNO))).isPresent();
    }

    @Test
    void givenACustomerWithoutCart_whenDeletingTwice_thenThereIsNoCartEitherTime() {
        carts.save(Cart.open(CartId.of(UUID.randomUUID()), new CartOwner.Customer(ANA), OPENED));
        delete.execute(new DeleteCustomerCart.Command(ANA));

        assertThat(delete.execute(new DeleteCustomerCart.Command(ANA)))
            .isEqualTo(new DeleteCustomerCart.Result.NoCart());
        assertThat(carts.size()).isZero();
    }
}
