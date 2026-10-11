package com.pijamaparty.store.catalog.application;

import java.util.Optional;

class InMemoryImageStoreContractTest extends ImageStoreContractTest<InMemoryImageStore> {

    @Override
    protected InMemoryImageStore createSubject() {
        return new InMemoryImageStore();
    }

    @Override
    protected Optional<byte[]> stored(String fileName) {
        return subject.file(fileName);
    }
}
