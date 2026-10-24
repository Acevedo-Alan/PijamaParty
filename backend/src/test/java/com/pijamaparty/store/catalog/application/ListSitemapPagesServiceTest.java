package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ListingFixture.category;
import static com.pijamaparty.store.catalog.application.ListingFixture.product;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.CategoryName;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T-E6-23: the catalog's pages in the sitemap. */
class ListSitemapPagesServiceTest {

    private final InMemoryCategoryStore categories = new InMemoryCategoryStore();
    private final InMemoryProductStore products = new InMemoryProductStore(categories);
    private final InMemoryProductListing listing = new InMemoryProductListing(categories, products);
    private final ListSitemapPages listSitemapPages = new ListSitemapPagesService(categories, listing);

    @Test
    void givenActiveAndInactiveCategoriesAndProducts_whenListingThePages_thenOnlyWhatVisitorsSeeIsListed() {
        Category summer = saved(category("Verano", true));
        Category winter = saved(category("Invierno", false));
        Category kids = saved(Category.create(CategoryId.of(UUID.randomUUID()), new CategoryName("Niños"),
            categories.findAll(), ListingFixture.T0));
        products.save(product(summer.id(), "Pijama", 0, List.of("M"), List.of("Rosa"), 1000));
        products.save(product(summer.id(), "Oculto", 1, List.of("M"), List.of("Rosa"), 1000, p -> p.hidden()));
        products.save(product(winter.id(), "De invierno", 2, List.of("M"), List.of("Rosa"), 1000));

        assertThat(listSitemapPages.execute()).isEqualTo(new ListSitemapPages.Result.Listed(
            List.of(summer.slug().value(), kids.slug().value()), List.of("pijama")));
    }

    private Category saved(Category category) {
        categories.save(category);
        return category;
    }
}
