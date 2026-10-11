package com.pijamaparty.store.catalog.application;

class InMemoryImageProcessorContractTest extends ImageProcessorContractTest<InMemoryImageProcessor> {

    @Override
    protected InMemoryImageProcessor createSubject() {
        return new InMemoryImageProcessor();
    }
}
