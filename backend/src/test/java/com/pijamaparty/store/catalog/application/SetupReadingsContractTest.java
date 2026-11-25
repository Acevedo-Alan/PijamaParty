package com.pijamaparty.store.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.api.CatalogSetup.Thing;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link SetupReadings} (UC-46): the active categories with a published product and no size guide, in the
 * categories' order; the published products without photos, newest first; at most the limit, with the total; and
 * whether the banner is published.
 */
public abstract class SetupReadingsContractTest<T extends SetupReadings> extends ContractTest<T> {

    /** An active or inactive category at {@code position}, with or without a size guide; answers its id. */
    protected abstract UUID givenCategory(String name, int position, boolean active, boolean sizeGuide);

    /** A product of {@code category}, published or hidden, created {@code minutesAgo}, with or without a photo. */
    protected abstract UUID givenProduct(UUID category, String name, boolean published, int minutesAgo, boolean photo);

    protected abstract void givenBanner(boolean published);

    @Test
    void givenCategoriesWithAndWithoutGuides_whenRead_thenOnlyActiveOnesWithAPublishedProductAndNoGuideInOrder() {
        givenProduct(givenCategory("Pijamas", 0, true, true), "Luna", true, 1, true);
        UUID batas = givenCategory("Batas", 3, true, false);
        givenProduct(batas, "Bata", true, 1, true);
        UUID ropaDeCasa = givenCategory("Ropa de casa", 1, true, false);
        givenProduct(ropaDeCasa, "Remerón", true, 1, true);
        givenCategory("Sin productos", 2, true, false);
        givenProduct(givenCategory("Inactiva", 4, false, false), "Viejo", true, 1, true);
        givenProduct(givenCategory("Solo ocultos", 5, true, false), "Escondido", false, 1, true);
        persistAndForget();

        var missing = subject.categoriesWithoutSizeGuide(1);

        assertThat(missing.first()).containsExactly(new Thing(ropaDeCasa, "Ropa de casa"));
        assertThat(missing.total()).isEqualTo(2);
    }

    @Test
    void givenProductsWithAndWithoutPhotos_whenRead_thenThePublishedWithoutPhotoNewestFirst() {
        UUID category = givenCategory("Pijamas", 0, true, true);
        givenProduct(category, "Con foto", true, 1, true);
        givenProduct(category, "Viejo sin foto", true, 30, false);
        givenProduct(category, "Nuevo sin foto", true, 5, false);
        givenProduct(category, "Oculto sin foto", false, 1, false);
        persistAndForget();

        var missing = subject.publishedProductsWithoutPhoto(5);

        assertThat(missing.first()).extracting(Thing::name).containsExactly("Nuevo sin foto", "Viejo sin foto");
        assertThat(missing.total()).isEqualTo(2);
    }

    @Test
    void givenTheBannerPublishedOrNot_whenRead_thenItSaysSo() {
        givenBanner(false);
        persistAndForget();
        assertThat(subject.homeBannerPublished()).isFalse();

        givenBanner(true);
        persistAndForget();
        assertThat(subject.homeBannerPublished()).isTrue();
    }
}
