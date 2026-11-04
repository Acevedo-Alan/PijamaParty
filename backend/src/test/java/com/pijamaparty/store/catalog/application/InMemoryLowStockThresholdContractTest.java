package com.pijamaparty.store.catalog.application;

class InMemoryLowStockThresholdContractTest extends LowStockThresholdContractTest<InMemoryLowStockThreshold> {

    @Override
    protected InMemoryLowStockThreshold createSubject() {
        return new InMemoryLowStockThreshold();
    }
}
