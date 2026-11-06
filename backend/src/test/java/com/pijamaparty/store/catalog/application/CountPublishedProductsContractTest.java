package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ListingFixture.product;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.CategoryId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Contract of {@link CountPublishedProducts} (UC-33): only published products count. */
public abstract class CountPublishedProductsContractTest<T extends CountPublishedProducts>
    extends ListedProductsContractTest<T> {

    private static final List<String> ROSA = List.of("Rosa");

    @Test
    void givenPublishedAndHiddenProducts_whenCounted_thenOnlyThePublishedCount() {
        CategoryId summer = category("Verano", true).id();
        stored(product(summer, "Publicado", 0, List.of("M"), ROSA, 1000));
        stored(product(summer, "Otro publicado", 1, List.of("M"), ROSA, 1000));
        stored(product(summer, "Oculto", 2, List.of("M"), ROSA, 1000, builder -> builder.hidden()));
        persistAndForget();

        assertThat(subject.publishedProducts()).isEqualTo(2);
    }
}
