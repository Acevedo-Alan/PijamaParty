package com.pijamaparty.store.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CategoryTest {

    private static final Instant CREATED = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-10T12:00:00Z");

    @Test
    void givenNoCategories_whenOneIsCreated_thenItIsActiveFirstAndItsSlugComesFromTheName() {
        Category category = create("Pijamas de Niñas", List.of());

        assertThat(category.slug()).isEqualTo(new Slug("pijamas-de-ninas"));
        assertThat(category.position()).isZero();
        assertThat(category.active()).isTrue();
        assertThat(category.createdAt()).isEqualTo(CREATED);
        assertThat(category.updatedAt()).isEqualTo(CREATED);
    }

    @Test
    void givenExistingCategories_whenOneIsCreated_thenItGoesLast() {
        Category first = create("Verano", List.of());
        Category second = create("Invierno", List.of(first));

        assertThat(create("Media estación", List.of(second, first)).position()).isEqualTo(2);
    }

    @Test
    void givenACategory_whenRenamed_thenItsSlugFollowsTheNewName() {
        Category category = create("Verano", List.of());

        category.rename(new CategoryName("Pijamas de verano"), LATER);

        assertThat(category.name().value()).isEqualTo("Pijamas de verano");
        assertThat(category.slug()).isEqualTo(new Slug("pijamas-de-verano"));
        assertThat(category.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void givenACategory_whenRenamedToTheSameName_thenNothingChanges() {
        Category category = create("Verano", List.of());

        category.rename(new CategoryName("Verano"), LATER);

        assertThat(category.updatedAt()).isEqualTo(CREATED);
    }

    @Test
    void givenADeactivatedCategory_whenActivated_thenItIsActiveAgain() {
        Category category = create("Verano", List.of());

        category.deactivate(LATER);
        assertThat(category.active()).isFalse();
        assertThat(category.updatedAt()).isEqualTo(LATER);

        Instant evenLater = LATER.plusSeconds(60);
        category.deactivate(evenLater);
        assertThat(category.updatedAt()).as("deactivating twice changes nothing").isEqualTo(LATER);

        category.activate(evenLater);
        assertThat(category.active()).isTrue();
        assertThat(category.updatedAt()).isEqualTo(evenLater);
    }

    @Test
    void givenACategory_whenCopied_thenChangingTheCopyLeavesTheOriginalUnchanged() {
        Category category = create("Verano", List.of());

        Category copy = category.copy();
        copy.deactivate(LATER);

        assertThat(category.active()).isTrue();
    }

    private static Category create(String name, List<Category> existing) {
        return Category.create(CategoryId.of(UUID.randomUUID()), new CategoryName(name), existing, CREATED);
    }
}
