package com.pijamaparty.store.ordering.adapter.out.cart;

import com.pijamaparty.store.cart.api.CartSummaries;
import com.pijamaparty.store.ordering.application.CartAmounts;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** The cart's amounts through the public API of the cart module, in the caller's transaction. */
@Component
class CartModuleAmounts implements CartAmounts {

    private final CartSummaries summaries;

    CartModuleAmounts(CartSummaries summaries) {
        this.summaries = summaries;
    }

    @Override
    public Optional<Amounts> of(Actor actor) {
        return summaries.of(actor).map(summary -> new Amounts(summary.subtotal(), summary.discount()));
    }
}
