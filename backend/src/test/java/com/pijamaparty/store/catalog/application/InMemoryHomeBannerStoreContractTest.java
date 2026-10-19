package com.pijamaparty.store.catalog.application;

class InMemoryHomeBannerStoreContractTest extends HomeBannerStoreContractTest<InMemoryHomeBannerStore> {

    @Override
    protected InMemoryHomeBannerStore createSubject() {
        return new InMemoryHomeBannerStore();
    }
}
