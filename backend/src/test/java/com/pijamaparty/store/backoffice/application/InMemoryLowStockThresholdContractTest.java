package com.pijamaparty.store.backoffice.application;

class InMemoryLowStockThresholdContractTest extends LowStockThresholdContractTest<InMemoryLowStockThreshold> {

    @Override
    protected InMemoryLowStockThreshold createSubject() {
        return new InMemoryLowStockThreshold(3);
    }
}
