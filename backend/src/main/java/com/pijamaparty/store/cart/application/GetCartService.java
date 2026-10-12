package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.CartOwner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetCartService implements GetCart {

    private final LoadCart loadCart;

    GetCartService(LoadCart loadCart) {
        this.loadCart = loadCart;
    }

    @Override
    @Transactional(readOnly = true)
    public CartView execute(Query query) {
        return CartOwner.of(query.actor())
            .flatMap(loadCart::findByOwner)
            .map(CartView::of)
            .orElse(CartView.empty());
    }
}
