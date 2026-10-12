package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ListingFixture.product;
import static com.pijamaparty.store.catalog.application.ListingFixture.variant;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.api.VariantDescription;
import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.Product;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VariantDescriptionsServiceTest {

    private final InMemoryCategoryStore categories = new InMemoryCategoryStore();
    private final InMemoryProductStore products = new InMemoryProductStore(categories);
    private final VariantDescriptionsService service =
        new VariantDescriptionsService(new InMemoryVariantDescriptionLookup(products));

    @Test
    void givenAKnownAndAnUnknownVariant_whenDescribingThem_thenOnlyTheKnownOneIsDescribed() {
        Category summer = ListingFixture.category("Verano", true);
        categories.save(summer);
        Product product = product(summer.id(), "Pijama", 0, List.of("M"), List.of("Rosa"), 5000);
        products.save(product);
        UUID known = variant(product, "M", "Rosa").value();

        assertThat(service.describe(List.of(known, UUID.randomUUID()))).isEqualTo(Map.of(known,
            new VariantDescription("Pijama", product.slug().value(), "M", "Rosa", Optional.empty())));
    }

    @Test
    void givenNoVariants_whenDescribingThem_thenThereAreNone() {
        assertThat(service.describe(List.of())).isEmpty();
    }
}
