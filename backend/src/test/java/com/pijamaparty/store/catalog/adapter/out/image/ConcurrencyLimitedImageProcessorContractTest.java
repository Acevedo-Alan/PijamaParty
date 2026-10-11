package com.pijamaparty.store.catalog.adapter.out.image;

import com.pijamaparty.store.catalog.application.ImageProcessorContractTest;
import com.pijamaparty.store.catalog.application.InMemoryImageProcessor;
import java.time.Duration;

/** The limit adds no behavior of its own to a conversion that gets a slot. */
class ConcurrencyLimitedImageProcessorContractTest
    extends ImageProcessorContractTest<ConcurrencyLimitedImageProcessor> {

    @Override
    protected ConcurrencyLimitedImageProcessor createSubject() {
        return new ConcurrencyLimitedImageProcessor(new InMemoryImageProcessor(), 2, Duration.ofSeconds(10));
    }
}
