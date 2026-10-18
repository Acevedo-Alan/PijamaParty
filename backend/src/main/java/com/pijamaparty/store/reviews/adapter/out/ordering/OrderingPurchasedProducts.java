package com.pijamaparty.store.reviews.adapter.out.ordering;

import com.pijamaparty.store.ordering.api.CustomerPurchases;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.reviews.application.PurchasedProducts;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** {@link PurchasedProducts} through the public API of ordering. */
@Component
class OrderingPurchasedProducts implements PurchasedProducts {

    private final CustomerPurchases purchases;

    OrderingPurchasedProducts(CustomerPurchases purchases) {
        this.purchases = purchases;
    }

    @Override
    public boolean boughtAnyOf(UserId customer, Set<UUID> variantIds) {
        return purchases.boughtAnyOf(customer, variantIds);
    }
}
