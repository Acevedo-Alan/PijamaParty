package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartId;
import com.pijamaparty.store.cart.domain.CartItemId;
import com.pijamaparty.store.cart.domain.CartOwner;
import com.pijamaparty.store.platform.application.IdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AddItemToCartService implements AddItemToCart {

    private final LoadCart loadCart;
    private final SaveCart saveCart;
    private final VariantAvailability availability;
    private final IdGenerator ids;
    private final Clock clock;

    AddItemToCartService(LoadCart loadCart, SaveCart saveCart, VariantAvailability availability, IdGenerator ids,
                         Clock clock) {
        this.loadCart = loadCart;
        this.saveCart = saveCart;
        this.availability = availability;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        Optional<CartOwner> owner = CartOwner.of(command.actor());
        if (owner.isEmpty()) {
            return new Result.Forbidden();
        }
        var variant = availability.of(command.variantId());
        if (variant.isEmpty()) {
            return new Result.VariantUnavailable(command.variantId());
        }
        Instant now = clock.instant();
        Cart cart = loadCart.lockByOwner(owner.get())
            .orElseGet(() -> Cart.open(CartId.of(ids.newId()), owner.get(), now));
        var outcome = cart.add(CartItemId.of(ids.newId()), command.variantId(), command.quantity(),
            variant.get().available(), variant.get().price(), now);
        return switch (outcome) {
            case Cart.AddOutcome.Added added -> {
                saveCart.save(cart);
                yield new Result.Added(CartView.of(cart));
            }
            case Cart.AddOutcome.ExceedsStock exceeds ->
                new Result.InsufficientStock(command.variantId(), exceeds.available());
        };
    }
}
