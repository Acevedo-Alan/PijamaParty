package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.api.CartSummaries;
import com.pijamaparty.store.cart.api.CartSummary;
import com.pijamaparty.store.cart.domain.CartOwner;
import com.pijamaparty.store.cart.domain.PricedCart;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-13: the actor's cart priced as every cart response (UC-09, UC-10). */
@Service
class CartSummariesService implements CartSummaries {

    private final LoadCart loadCart;
    private final VariantAvailability availability;
    private final Coupons coupons;

    CartSummariesService(LoadCart loadCart, VariantAvailability availability, Coupons coupons) {
        this.loadCart = loadCart;
        this.availability = availability;
        this.coupons = coupons;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CartSummary> of(Actor actor) {
        return CartOwner.of(actor)
            .flatMap(loadCart::findByOwner)
            .map(cart -> CartPricing.priced(cart, availability, coupons))
            .filter(PricedCart::hasAvailableLines)
            .map(priced -> new CartSummary(priced.subtotal(), priced.discount(), priced.total()));
    }
}
