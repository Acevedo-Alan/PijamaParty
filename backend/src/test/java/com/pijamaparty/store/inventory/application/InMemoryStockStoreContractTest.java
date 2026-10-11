package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.inventory.domain.VariantId;
import java.time.Instant;

class InMemoryStockStoreContractTest extends StockStoreContractTest<InMemoryStockStore> {

    @Override
    protected InMemoryStockStore createSubject() {
        return new InMemoryStockStore();
    }

    @Override
    protected void givenStockLevel(VariantId variantId, Instant at) {
        subject.open(variantId, at);
    }
}
