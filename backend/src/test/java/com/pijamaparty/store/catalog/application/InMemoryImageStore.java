package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.ProductImage;
import com.pijamaparty.store.catalog.domain.StorageKey;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Fake of {@link ImageStore}: the files by name. */
public class InMemoryImageStore implements ImageStore {

    private final Map<String, byte[]> files = new ConcurrentHashMap<>();

    @Override
    public void save(StorageKey key, List<ImageProcessor.Rendition> renditions) {
        renditions.forEach(rendition -> files.put(key.fileName(rendition.width()), rendition.webp().clone()));
    }

    @Override
    public void delete(StorageKey key) {
        ProductImage.WIDTHS.forEach(width -> files.remove(key.fileName(width)));
    }

    public Optional<byte[]> file(String fileName) {
        return Optional.ofNullable(files.get(fileName)).map(byte[]::clone);
    }

    public Set<String> fileNames() {
        return Set.copyOf(files.keySet());
    }
}
