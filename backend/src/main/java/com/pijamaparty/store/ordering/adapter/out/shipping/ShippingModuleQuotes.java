package com.pijamaparty.store.ordering.adapter.out.shipping;

import com.pijamaparty.store.ordering.application.ShippingQuotes;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.shipping.api.Province;
import com.pijamaparty.store.shipping.api.ShippingCosts;
import com.pijamaparty.store.shipping.api.ShippingQuote;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Shipping costs through the public API of the shipping module, in the caller's transaction. */
@Component
class ShippingModuleQuotes implements ShippingQuotes {

    private final ShippingCosts costs;

    ShippingModuleQuotes(ShippingCosts costs) {
        this.costs = costs;
    }

    @Override
    public Optional<Money> costTo(Province province, Money subtotalAfterDiscount) {
        return switch (costs.quote(province, subtotalAfterDiscount)) {
            case ShippingQuote.Cost cost -> Optional.of(cost.amount());
            case ShippingQuote.NoShipping none -> Optional.empty();
        };
    }
}
