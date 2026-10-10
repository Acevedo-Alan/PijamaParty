package com.pijamaparty.store.catalog.application;

class InMemorySkuGeneratorContractTest extends SkuGeneratorContractTest<InMemorySkuGenerator> {

    @Override
    protected InMemorySkuGenerator createSubject() {
        return new InMemorySkuGenerator();
    }
}
