package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetShippingCostService implements GetShippingCost {

    private final CartAmounts cartAmounts;
    private final ShippingQuotes shippingQuotes;

    GetShippingCostService(CartAmounts cartAmounts, ShippingQuotes shippingQuotes) {
        this.cartAmounts = cartAmounts;
        this.shippingQuotes = shippingQuotes;
    }

    /** As the buyer: Row Level Security lets it read only its own cart (RNF-27). */
    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        Optional<CartAmounts.Amounts> cart = cartAmounts.of(query.actor());
        if (cart.isEmpty()) {
            return new Result.EmptyCart();
        }
        CartAmounts.Amounts amounts = cart.get();
        Optional<Money> shipping = shippingQuotes.costTo(query.province(), amounts.afterDiscount());
        if (shipping.isEmpty()) {
            return new Result.NoShipping(query.province());
        }
        return new Result.Quoted(query.province(), amounts.subtotal(), amounts.discount(), shipping.get(),
            amounts.afterDiscount().plus(shipping.get()));
    }
}
