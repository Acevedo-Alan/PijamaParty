package com.pijamaparty.store.ordering.application;

import java.util.List;
import java.util.UUID;

class InMemoryLinePhotosContractTest extends LinePhotosContractTest<InMemoryLinePhotos> {

    @Override
    protected InMemoryLinePhotos createSubject() {
        return new InMemoryLinePhotos();
    }

    @Override
    protected void givenVariant(UUID variantId, boolean sold) {
        // A variant without photos is left out, as an unknown one.
    }

    @Override
    protected void givenPhotoOfItsColor(UUID variantId, String storageKey) {
        subject.add(variantId, new LinePhotos.Photo(PLACEHOLDER, List.of(400, 600, 800, 1200).stream()
            .map(width -> new LinePhotos.PhotoFile(width, storageKey + "-" + width + ".webp"))
            .toList()));
    }
}
