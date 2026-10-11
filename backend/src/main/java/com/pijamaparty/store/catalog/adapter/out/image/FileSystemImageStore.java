package com.pijamaparty.store.catalog.adapter.out.image;

import com.pijamaparty.store.catalog.application.ImageProcessor;
import com.pijamaparty.store.catalog.application.ImageStore;
import com.pijamaparty.store.catalog.domain.ProductImage;
import com.pijamaparty.store.catalog.domain.StorageKey;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The photos' files in the uploads volume, which Caddy serves read-only under {@code /uploads} (UC-06). Each file is
 * written under a temporary name and then moved into place, so Caddy never serves half a file; the temporary names do
 * not end in {@code .webp}, which is all Caddy serves there.
 */
@Component
class FileSystemImageStore implements ImageStore {

    private static final Logger log = LoggerFactory.getLogger(FileSystemImageStore.class);

    private final Path directory;

    FileSystemImageStore(@Value("${store.images.uploads-dir}") String directory) {
        // An empty UPLOADS_DIR would silently mean the working directory.
        if (directory.isBlank()) {
            throw new IllegalStateException("store.images.uploads-dir (UPLOADS_DIR) is empty");
        }
        this.directory = Path.of(directory);
    }

    @Override
    public void save(StorageKey key, List<ImageProcessor.Rendition> renditions) {
        List<Path> written = new ArrayList<>();
        try {
            Files.createDirectories(directory);
            for (ImageProcessor.Rendition rendition : renditions) {
                Path temporary = Files.createTempFile(directory, ".upload-", ".tmp");
                written.add(temporary);
                Files.write(temporary, rendition.webp());
                Path target = directory.resolve(key.fileName(rendition.width()));
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                written.add(target);
            }
        } catch (IOException e) {
            written.forEach(FileSystemImageStore::deleteQuietly);
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(StorageKey key) {
        ProductImage.WIDTHS.forEach(width -> deleteQuietly(directory.resolve(key.fileName(width))));
    }

    /** The storage key is not personal data, so it may be logged. */
    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not remove the image file {}; it is left behind", file.getFileName(), e);
        }
    }
}
