package com.pijamaparty.store.ordering.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Fake of {@link LinePhotos}: a variant has a photo once {@link #add} gives it one. */
public class InMemoryLinePhotos implements LinePhotos {

    private final Map<UUID, Photo> photos = new ConcurrentHashMap<>();

    public void add(UUID variantId, Photo photo) {
        photos.put(variantId, photo);
    }

    @Override
    public Map<UUID, Photo> photosOf(Collection<UUID> variantIds) {
        Map<UUID, Photo> found = new HashMap<>();
        variantIds.forEach(id -> {
            Photo photo = photos.get(id);
            if (photo != null) {
                found.put(id, photo);
            }
        });
        return found;
    }
}
