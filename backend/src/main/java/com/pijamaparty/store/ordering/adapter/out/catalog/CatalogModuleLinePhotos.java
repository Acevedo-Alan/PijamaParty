package com.pijamaparty.store.ordering.adapter.out.catalog;

import com.pijamaparty.store.catalog.api.VariantDescription;
import com.pijamaparty.store.catalog.api.VariantDescriptions;
import com.pijamaparty.store.ordering.application.LinePhotos;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The photo of each sold variant, through the catalog's public API, as a cart line shows it. */
@Component
class CatalogModuleLinePhotos implements LinePhotos {

    private final VariantDescriptions variantDescriptions;

    CatalogModuleLinePhotos(VariantDescriptions variantDescriptions) {
        this.variantDescriptions = variantDescriptions;
    }

    @Override
    public Map<UUID, Photo> photosOf(Collection<UUID> variantIds) {
        Map<UUID, Photo> photos = new HashMap<>();
        if (variantIds.isEmpty()) {
            return photos;
        }
        variantDescriptions.describe(variantIds).forEach((id, description) ->
            description.photo().ifPresent(photo -> photos.put(id, toPhoto(photo))));
        return photos;
    }

    private static Photo toPhoto(VariantDescription.Photo photo) {
        return new Photo(photo.placeholder(), photo.files().stream()
            .map(file -> new PhotoFile(file.width(), file.fileName()))
            .toList());
    }
}
