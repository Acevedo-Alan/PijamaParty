package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.api.VariantDescription;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductImage;
import com.pijamaparty.store.catalog.domain.Variant;
import com.pijamaparty.store.catalog.domain.VariantId;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Fake of {@link VariantDescriptionLookup} over the product fake. */
public class InMemoryVariantDescriptionLookup implements VariantDescriptionLookup {

    private final InMemoryProductStore products;

    public InMemoryVariantDescriptionLookup(InMemoryProductStore products) {
        this.products = products;
    }

    @Override
    public Map<VariantId, VariantDescription> describe(Collection<VariantId> variants) {
        Map<VariantId, VariantDescription> described = new HashMap<>();
        for (Product product : products.all()) {
            product.variants().stream()
                .filter(variant -> variants.contains(variant.id()))
                .forEach(variant -> described.put(variant.id(), describe(product, variant)));
        }
        return described;
    }

    /** The photo is the first of the variant's color, or else the first of the whole product. */
    private static VariantDescription describe(Product product, Variant variant) {
        Optional<VariantDescription.Photo> photo = product.images().stream()
            .filter(image -> image.color().map(variant.color()::equals).orElse(true))
            .min(Comparator.comparing((ProductImage image) -> image.color().isEmpty()))
            .map(image -> new VariantDescription.Photo(image.placeholder().value(), ProductImage.WIDTHS.stream()
                .map(width -> new VariantDescription.PhotoFile(width, image.storageKey().fileName(width)))
                .toList()));
        return new VariantDescription(product.details().name().value(), product.slug().value(),
            variant.size().value(), variant.color().value(), photo);
    }
}
