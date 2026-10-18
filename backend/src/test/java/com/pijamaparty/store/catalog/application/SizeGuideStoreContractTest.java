package com.pijamaparty.store.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Size;
import com.pijamaparty.store.catalog.domain.SizeGuide;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link LoadSizeGuides}, {@link SaveSizeGuide} and {@link RemoveSizeGuide}. */
public abstract class SizeGuideStoreContractTest<T extends LoadSizeGuides & SaveSizeGuide & RemoveSizeGuide>
    extends ContractTest<T> {

    /** Whole seconds: PostgreSQL keeps microseconds, so the instants survive the round trip. */
    private static final Instant SAVED_AT = Instant.parse("2026-10-18T12:00:00Z");

    /** A new stored category, without products. */
    protected abstract CategoryId givenACategory();

    /** Deletes the category as UC-05 does. */
    protected abstract void deleteCategory(CategoryId category);

    @Test
    void givenASavedGuide_whenLoadingIt_thenItHasTheSameColumnsRowsAndNoteInTheirOrder() {
        CategoryId category = givenACategory();
        SizeGuide guide = new SizeGuide(category, List.of("Busto (cm)", "Cintura (cm)", "Cadera (cm)"),
            List.of(row("S", "83–87", "65–69", "91–95"), row("M", "88–93", "70–75", ""), row("XS", "78–82", "", "")),
            Optional.of("Medí sobre la ropa interior.\nSi dudás, elegí el talle mayor."), SAVED_AT);

        assertThat(subject.save(guide)).isEqualTo(SaveSizeGuide.Outcome.SAVED);
        persistAndForget();

        assertThat(subject.findByCategory(category)).contains(guide);
    }

    @Test
    void givenASavedGuide_whenAnotherIsSavedForTheSameCategory_thenItReplacesItWhole() {
        CategoryId category = givenACategory();
        subject.save(new SizeGuide(category, List.of("Busto", "Cintura"), List.of(row("S", "1", "2"),
            row("M", "3", "4")), Optional.of("Nota"), SAVED_AT));
        persistAndForget();
        SizeGuide replacement = new SizeGuide(category, List.of("Edad"), List.of(row("4", "4 años")),
            Optional.empty(), SAVED_AT.plusSeconds(60));

        assertThat(subject.save(replacement)).isEqualTo(SaveSizeGuide.Outcome.SAVED);
        persistAndForget();

        assertThat(subject.findByCategory(category)).contains(replacement);
    }

    @Test
    void givenGuidesOfTwoCategories_whenOneIsRemoved_thenOnlyItsGuideIsGone() {
        CategoryId women = givenACategory();
        CategoryId kids = givenACategory();
        subject.save(guide(women));
        SizeGuide kept = guide(kids);
        subject.save(kept);
        persistAndForget();

        subject.remove(women);
        subject.remove(women);
        persistAndForget();

        assertThat(subject.findByCategory(women)).isEmpty();
        assertThat(subject.findByCategory(kids)).contains(kept);
    }

    @Test
    void givenACategoryWithAGuide_whenTheCategoryIsDeleted_thenItsGuideIsGone() {
        CategoryId category = givenACategory();
        subject.save(guide(category));
        persistAndForget();

        deleteCategory(category);
        persistAndForget();

        assertThat(subject.findByCategory(category)).isEmpty();
    }

    @Test
    void givenAnUnknownCategory_whenItsGuideIsSaved_thenItIsNotSavedAndTheStoreStaysUsable() {
        CategoryId unknown = CategoryId.of(UUID.randomUUID());

        assertThat(subject.save(guide(unknown))).isEqualTo(SaveSizeGuide.Outcome.CATEGORY_NOT_FOUND);

        CategoryId category = givenACategory();
        assertThat(subject.save(guide(category))).isEqualTo(SaveSizeGuide.Outcome.SAVED);
        persistAndForget();
        assertThat(subject.findByCategory(unknown)).isEmpty();
        assertThat(subject.findByCategory(category)).isPresent();
    }

    @Test
    void givenACategoryWithoutAGuide_whenLoading_thenItIsEmpty() {
        assertThat(subject.findByCategory(givenACategory())).isEmpty();
    }

    private static SizeGuide guide(CategoryId category) {
        return new SizeGuide(category, List.of("Busto"), List.of(row("S", "83–87")), Optional.empty(), SAVED_AT);
    }

    private static SizeGuide.Row row(String size, String... values) {
        return new SizeGuide.Row(new Size(size), List.of(values));
    }
}
