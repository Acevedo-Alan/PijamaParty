package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.UUID;

class InMemoryPurchasedProductsContractTest extends PurchasedProductsContractTest<InMemoryPurchasedProducts> {

    @Override
    protected InMemoryPurchasedProducts createSubject() {
        return new InMemoryPurchasedProducts();
    }

    @Override
    protected void givenOrder(UserId customer, String status, UUID variant) {
        if (BOUGHT.contains(status)) {
            subject.bought(customer, variant);
        }
    }
}
