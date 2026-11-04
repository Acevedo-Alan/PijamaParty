package com.pijamaparty.store.settings.application;

class InMemoryStoreSettingsStoreContractTest extends StoreSettingsStoreContractTest<InMemoryStoreSettingsStore> {

    @Override
    protected InMemoryStoreSettingsStore createSubject() {
        return new InMemoryStoreSettingsStore();
    }
}
