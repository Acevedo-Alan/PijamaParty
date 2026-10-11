package com.pijamaparty.store.catalog.adapter.out.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.catalog.application.ImageProcessor;
import com.pijamaparty.store.catalog.application.ImageStoreContractTest;
import com.pijamaparty.store.catalog.domain.StorageKey;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemImageStoreContractTest extends ImageStoreContractTest<FileSystemImageStore> {

    @TempDir
    Path uploads;

    @Override
    protected FileSystemImageStore createSubject() {
        return new FileSystemImageStore(uploads.toString());
    }

    @Override
    protected Optional<byte[]> stored(String fileName) {
        Path file = uploads.resolve(fileName);
        try {
            return Files.exists(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void givenSavedRenditions_whenTheDirectoryIsListed_thenOnlyTheirWebpFilesAreThere() throws IOException {
        StorageKey key = StorageKey.of(UUID.randomUUID());

        subject.save(key, List.of(new ImageProcessor.Rendition(400, new byte[] {1}),
            new ImageProcessor.Rendition(800, new byte[] {2})));

        try (Stream<Path> files = Files.list(uploads)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                .containsExactlyInAnyOrder(key.fileName(400), key.fileName(800));
        }
    }

    @Test
    void givenAMissingDirectory_whenSaving_thenItIsCreated() {
        var store = new FileSystemImageStore(uploads.resolve("nested").toString());
        StorageKey key = StorageKey.of(UUID.randomUUID());

        store.save(key, List.of(new ImageProcessor.Rendition(400, new byte[] {1})));

        assertThat(uploads.resolve("nested").resolve(key.fileName(400))).exists();
    }

    @Test
    void givenAnEmptyDirectorySetting_whenCreated_thenItRefusesToStart() {
        assertThatThrownBy(() -> new FileSystemImageStore(" ")).hasMessageContaining("UPLOADS_DIR");
    }
}
