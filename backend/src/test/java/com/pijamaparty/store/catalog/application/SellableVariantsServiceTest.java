package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ListingFixture.product;
import static com.pijamaparty.store.catalog.application.ListingFixture.variant;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.platform.domain.Money;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SellableVariantsServiceTest {

    private final InMemoryCategoryStore categories = new InMemoryCategoryStore();
    private final InMemoryProductStore products = new InMemoryProductStore(categories);
    private final SellableVariantsService service =
        new SellableVariantsService(new InMemorySellableVariantPrices(categories, products));

    @Test
    void givenASellableAndAnUnknownVariant_whenAskingTheirPrices_thenOnlyTheSellableOneHasAPrice() {
        Category summer = ListingFixture.category("Verano", true);
        categories.save(summer);
        Product product = product(summer.id(), "Pijama", 0, List.of("M"), List.of("Rosa"), 5000);
        products.save(product);
        UUID sellable = variant(product, "M", "Rosa").value();

        assertThat(service.prices(List.of(sellable, UUID.randomUUID()))).isEqualTo(Map.of(sellable, Money.of(5000)));
    }

    @Test
    void givenNoVariants_whenAskingTheirPrices_thenThereAreNone() {
        assertThat(service.prices(List.of())).isEmpty();
    }
}
