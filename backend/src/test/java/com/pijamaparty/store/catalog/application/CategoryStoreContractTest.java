package com.pijamaparty.store.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.CategoryName;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link LoadCategories}, {@link SaveCategory} and {@link RemoveCategory}. */
public abstract class CategoryStoreContractTest<T extends LoadCategories & SaveCategory & RemoveCategory>
    extends ContractTest<T> {

    /** Whole seconds: PostgreSQL keeps microseconds, so the instants survive the round trip. */
    private static final Instant CREATED = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-10T12:00:00Z");

    /** Gives the category a product, as UC-06 will. */
    protected abstract void givenAProductIn(CategoryId category);

    @Test
    void givenASavedCategory_whenLoadingIt_thenItHasTheSameState() {
        Category saved = category("Pijamas de Niñas", List.of());

        assertThat(subject.add(saved)).isEqualTo(SaveCategory.Outcome.SAVED);
        persistAndForget();

        assertThat(subject.findById(saved.id())).get().usingRecursiveComparison().isEqualTo(saved);
    }

    @Test
    void givenASavedCategory_whenItChangesAndIsSavedAgain_thenTheChangesAreKept() {
        Category category = category("Verano", List.of());
        subject.add(category);
        persistAndForget();

        Category loaded = subject.findById(category.id()).orElseThrow();
        loaded.rename(new CategoryName("Pijamas de verano"), LATER);
        loaded.deactivate(LATER);
        assertThat(subject.update(loaded)).isEqualTo(SaveCategory.Outcome.SAVED);
        persistAndForget();

        assertThat(subject.findById(category.id())).get().usingRecursiveComparison().isEqualTo(loaded);
    }

    @Test
    void givenSeveralCategories_whenListingThem_thenAllComeByPositionActiveOrNot() {
        Category first = category("Verano", List.of());
        Category second = category("Invierno", List.of(first));
        Category third = category("Niños", List.of(first, second));
        third.deactivate(LATER);
        subject.add(third);
        subject.add(first);
        subject.add(second);
        persistAndForget();

        assertThat(subject.findAll()).extracting(Category::id).containsExactly(first.id(), second.id(), third.id());
    }

    @Test
    void givenASlugInUse_whenAnotherCategoryWithTheSameSlugIsSaved_thenItIsNotSavedAndTheStoreStaysUsable() {
        Category existing = category("Pijamas", List.of());
        subject.add(existing);

        Category clash = category("PIJAMAS", List.of(existing));
        assertThat(subject.add(clash)).isEqualTo(SaveCategory.Outcome.SLUG_ALREADY_USED);

        Category other = category("Batas", List.of(existing));
        assertThat(subject.add(other)).isEqualTo(SaveCategory.Outcome.SAVED);
        persistAndForget();
        assertThat(subject.findAll()).extracting(Category::id).containsExactly(existing.id(), other.id());
    }

    @Test
    void givenASlugInUse_whenAnotherCategoryIsRenamedToIt_thenTheRenameIsNotSaved() {
        Category summer = category("Verano", List.of());
        Category winter = category("Invierno", List.of(summer));
        subject.add(summer);
        subject.add(winter);
        persistAndForget();

        Category loaded = subject.findById(winter.id()).orElseThrow();
        loaded.rename(new CategoryName("verano"), LATER);

        assertThat(subject.update(loaded)).isEqualTo(SaveCategory.Outcome.SLUG_ALREADY_USED);
        persistAndForget();
        assertThat(subject.findById(winter.id()).orElseThrow().name().value()).isEqualTo("Invierno");
    }

    @Test
    void givenACategoryWithoutProducts_whenRemoved_thenItIsGone() {
        Category category = category("Verano", List.of());
        subject.add(category);
        persistAndForget();

        assertThat(subject.remove(category.id())).isEqualTo(RemoveCategory.Outcome.REMOVED);
        persistAndForget();

        assertThat(subject.findById(category.id())).isEmpty();
        assertThat(subject.remove(category.id())).isEqualTo(RemoveCategory.Outcome.NOT_FOUND);
    }

    @Test
    void givenACategoryWithProducts_whenRemoved_thenItStaysAndTheStoreStaysUsable() {
        Category category = category("Verano", List.of());
        subject.add(category);
        persistAndForget();
        givenAProductIn(category.id());

        assertThat(subject.remove(category.id())).isEqualTo(RemoveCategory.Outcome.HAS_PRODUCTS);

        persistAndForget();
        assertThat(subject.findById(category.id())).isPresent();
    }

    @Test
    void givenACategoryRemovedAfterItWasLoaded_whenItIsUpdated_thenItIsNotBroughtBack() {
        Category category = category("Verano", List.of());
        subject.add(category);
        persistAndForget();
        Category loaded = subject.findById(category.id()).orElseThrow();
        subject.remove(category.id());
        persistAndForget();

        loaded.deactivate(LATER);
        assertThat(subject.update(loaded)).isEqualTo(SaveCategory.Outcome.NOT_FOUND);

        persistAndForget();
        assertThat(subject.findById(category.id())).isEmpty();
    }

    @Test
    void givenAnUnknownId_whenLoading_thenItIsEmpty() {
        assertThat(subject.findById(CategoryId.of(UUID.randomUUID()))).isEmpty();
    }

    private static Category category(String name, List<Category> existing) {
        return Category.create(CategoryId.of(UUID.randomUUID()), new CategoryName(name), existing, CREATED);
    }
}
