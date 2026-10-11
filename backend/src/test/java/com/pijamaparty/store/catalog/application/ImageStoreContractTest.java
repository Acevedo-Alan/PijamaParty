package com.pijamaparty.store.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.StorageKey;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link ImageStore} (UC-06). */
public abstract class ImageStoreContractTest<T extends ImageStore> extends ContractTest<T> {

    private static final List<ImageProcessor.Rendition> RENDITIONS = List.of(
        new ImageProcessor.Rendition(400, new byte[] {1}),
        new ImageProcessor.Rendition(800, new byte[] {2, 2}),
        new ImageProcessor.Rendition(1200, new byte[] {3, 3, 3}));

    /** The content of a stored file, read where the photos are served from. */
    protected abstract Optional<byte[]> stored(String fileName);

    @Test
    void givenRenditions_whenSaved_thenEachIsAFileNamedAfterTheKeyAndWidth() {
        StorageKey key = StorageKey.of(UUID.randomUUID());

        subject.save(key, RENDITIONS);

        assertThat(stored(key.value() + "-400.webp")).hasValueSatisfying(file -> assertThat(file).containsExactly(1));
        assertThat(stored(key.value() + "-800.webp")).hasValueSatisfying(file -> assertThat(file).containsExactly(2, 2));
        assertThat(stored(key.value() + "-1200.webp"))
            .hasValueSatisfying(file -> assertThat(file).containsExactly(3, 3, 3));
    }

    @Test
    void givenTwoPhotos_whenOneIsDeleted_thenOnlyItsFilesGo() {
        StorageKey deleted = StorageKey.of(UUID.randomUUID());
        StorageKey kept = StorageKey.of(UUID.randomUUID());
        subject.save(deleted, RENDITIONS);
        subject.save(kept, RENDITIONS);

        subject.delete(deleted);

        assertThat(List.of(400, 800, 1200)).allSatisfy(width -> {
            assertThat(stored(deleted.fileName(width))).isEmpty();
            assertThat(stored(kept.fileName(width))).isPresent();
        });
    }

    @Test
    void givenAKeyWithoutFiles_whenDeleted_thenNothingHappens() {
        subject.delete(StorageKey.of(UUID.randomUUID()));
    }
}
