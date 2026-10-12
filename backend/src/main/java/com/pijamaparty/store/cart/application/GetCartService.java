package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.CartOwner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetCartService implements GetCart {

    private final LoadCart loadCart;
    private final VariantAvailability availability;
    private final Coupons coupons;
    private final ItemDescriptions descriptions;

    GetCartService(LoadCart loadCart, VariantAvailability availability, Coupons coupons,
                   ItemDescriptions descriptions) {
        this.loadCart = loadCart;
        this.availability = availability;
        this.coupons = coupons;
        this.descriptions = descriptions;
    }

    @Override
    @Transactional(readOnly = true)
    public CartView execute(Query query) {
        return CartOwner.of(query.actor())
            .flatMap(loadCart::findByOwner)
            .map(cart -> CartPricing.view(cart, availability, coupons, descriptions))
            .orElse(CartView.empty());
    }
}
