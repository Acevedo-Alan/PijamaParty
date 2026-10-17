package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartId;
import com.pijamaparty.store.cart.domain.CartOwner;
import com.pijamaparty.store.platform.application.IdGenerator;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MergeGuestCartService implements MergeGuestCart {

    private final LoadCart loadCart;
    private final SaveCart saveCart;
    private final DeleteCart deleteCart;
    private final VariantAvailability availability;
    private final IdGenerator ids;
    private final Clock clock;

    MergeGuestCartService(LoadCart loadCart, SaveCart saveCart, DeleteCart deleteCart,
                          VariantAvailability availability, IdGenerator ids, Clock clock) {
        this.loadCart = loadCart;
        this.saveCart = saveCart;
        this.deleteCart = deleteCart;
        this.availability = availability;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        Actor actor = command.actor();
        if (!actor.is(Role.CUSTOMER) || actor.cartToken().isEmpty()) {
            return new Result.NoGuestCart();
        }
        var customer = new CartOwner.Customer(actor.userId().orElseThrow());
        Optional<Cart> guest = loadCart.findByOwner(new CartOwner.Guest(actor.cartToken().get().hash()));
        // Deleted first: the customer's cart takes over the ids of the guest's lines. A sign-in of the same browser at
        // the same time waits for this delete and then finds nothing to delete, so the lines join only once.
        if (guest.isEmpty() || !deleteCart.delete(guest.get().id())) {
            return new Result.NoGuestCart();
        }
        Instant now = clock.instant();
        Cart cart = loadCart.lockByOwner(customer).orElseGet(() -> Cart.open(CartId.of(ids.newId()), customer, now));
        if (cart.merge(guest.get(), CartPricing.sellableIn(guest.get(), availability), now)) {
            saveCart.save(cart);
        }
        return new Result.Merged();
    }
}
