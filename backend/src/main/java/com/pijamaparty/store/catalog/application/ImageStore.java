package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.StorageKey;
import java.util.List;

/**
 * Outbound port: the files of the photos, served by Caddy under {@code /uploads} (UC-06). Each rendition is the file
 * {@link StorageKey#fileName(int)}. Files are written and removed outside any database transaction.
 */
public interface ImageStore {

    /** Writes every rendition; a failure leaves none of them behind and throws. */
    void save(StorageKey key, List<ImageProcessor.Rendition> renditions);

    /** Removes the renditions of the key, if any. Never throws: a file that cannot be removed is left behind. */
    void delete(StorageKey key);
}
