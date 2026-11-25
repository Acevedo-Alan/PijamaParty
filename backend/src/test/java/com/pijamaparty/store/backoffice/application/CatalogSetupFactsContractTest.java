package com.pijamaparty.store.backoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.backoffice.domain.SetupFacts.Thing;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link CatalogSetupFacts} (UC-46): the active categories with a published product and no size guide, in
 * the categories' order; the published products without photos, newest first; each at most the limit asked, with the
 * total; and whether the banner is published.
 */
public abstract class CatalogSetupFactsContractTest<T extends CatalogSetupFacts> extends ContractTest<T> {

    /** An active or inactive category at {@code position}, with or without a size guide; answers its id. */
    protected abstract UUID givenCategory(String name, int position, boolean active, boolean sizeGuide);

    /** A product of {@code category}, published or hidden, created {@code minutesAgo}, with or without a photo. */
    protected abstract UUID givenProduct(UUID category, String name, boolean published, int minutesAgo, boolean photo);

    protected abstract void givenBanner(boolean published);

    @Test
    void givenCategoriesWithAndWithoutGuidesAndProducts_whenAsked_thenOnlyActiveOnesWithAPublishedProductAndNoGuide() {
        UUID pijamas = givenCategory("Pijamas", 0, true, true);
        givenProduct(pijamas, "Luna", true, 1, true);
        UUID ropaDeCasa = givenCategory("Ropa de casa", 1, true, false);
        givenProduct(ropaDeCasa, "Remerón", true, 1, true);
        givenCategory("Batas", 2, true, false);
        UUID oculta = givenCategory("Oculta", 3, false, false);
        givenProduct(oculta, "Viejo", true, 1, true);
        UUID soloOcultos = givenCategory("Solo ocultos", 4, true, false);
        givenProduct(soloOcultos, "Escondido", false, 1, true);
        persistAndForget();

        var missing = subject.categoriesWithoutSizeGuide(5);

        assertThat(missing.first()).containsExactly(new Thing(ropaDeCasa, "Ropa de casa"));
        assertThat(missing.total()).isEqualTo(1);
    }

    @Test
    void givenThreeCategoriesWithoutGuides_whenAskedForTwo_thenTheFirstTwoInOrderAndTheTotal() {
        for (int position = 0; position < 3; position++) {
            givenProduct(givenCategory("Categoría " + position, 2 - position, true, false), "P" + position, true, 1,
                true);
        }
        persistAndForget();

        var missing = subject.categoriesWithoutSizeGuide(2);

        assertThat(missing.first()).extracting(Thing::name).containsExactly("Categoría 2", "Categoría 1");
        assertThat(missing.total()).isEqualTo(3);
    }

    @Test
    void givenPublishedAndHiddenProductsWithAndWithoutPhotos_whenAsked_thenThePublishedWithoutPhotoNewestFirst() {
        UUID category = givenCategory("Pijamas", 0, true, true);
        givenProduct(category, "Con foto", true, 1, true);
        givenProduct(category, "Viejo sin foto", true, 30, false);
        givenProduct(category, "Nuevo sin foto", true, 5, false);
        givenProduct(category, "Oculto sin foto", false, 1, false);
        persistAndForget();

        var missing = subject.publishedProductsWithoutPhoto(1);

        assertThat(missing.first()).extracting(Thing::name).containsExactly("Nuevo sin foto");
        assertThat(missing.total()).isEqualTo(2);
    }

    @Test
    void givenNothingMissing_whenAsked_thenBothListsAreEmpty() {
        givenProduct(givenCategory("Pijamas", 0, true, true), "Luna", true, 1, true);
        persistAndForget();

        assertThat(subject.categoriesWithoutSizeGuide(5).total()).isZero();
        assertThat(subject.publishedProductsWithoutPhoto(5).first()).isEmpty();
    }

    @Test
    void givenTheBannerPublishedOrNot_whenAsked_thenItSaysSo() {
        givenBanner(false);
        persistAndForget();
        assertThat(subject.homeBannerPublished()).isFalse();

        givenBanner(true);
        persistAndForget();
        assertThat(subject.homeBannerPublished()).isTrue();
    }
}
