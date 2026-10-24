package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.CreateCategoryServiceTest.ADMIN;
import static com.pijamaparty.store.catalog.application.CreateCategoryServiceTest.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.CategoryName;
import com.pijamaparty.store.catalog.domain.SizeGuideForm;
import com.pijamaparty.store.catalog.domain.SizeGuideForm.RowForm;
import com.pijamaparty.store.platform.domain.Actor;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RNF-31.5: the administrator opens, edits and deletes the size guide of a category. */
class SizeGuideServicesTest {

    private static final SizeGuideForm WOMEN = new SizeGuideForm(List.of("Busto (cm)", "Cintura (cm)"),
        List.of(new RowForm("S", List.of("83–87", "65–69")), new RowForm("M", List.of("88–93", "70–75"))),
        "Medí sobre la ropa interior.");
    private static final Actor VISITOR = Actor.anonymous(Optional.empty(), Optional.empty());

    private final InMemoryCategoryStore categories = new InMemoryCategoryStore();
    private final InMemorySizeGuideStore guides = new InMemorySizeGuideStore(categories);
    private final GetSizeGuide get = new GetSizeGuideService(categories, guides);
    private final EditSizeGuide edit = new EditSizeGuideService(categories, guides, guides,
        new InMemoryCatalogActivity(), Clock.fixed(NOW, ZoneOffset.UTC));
    private final DeleteSizeGuide delete = new DeleteSizeGuideService(categories, guides, guides,
        new InMemoryCatalogActivity());

    private final Category women = saved("Pijamas de mujer");
    private final CategoryId unknown = CategoryId.of(UUID.randomUUID());

    @Test
    void givenACategoryWithoutAGuide_whenTheAdministratorOpensIt_thenItSeesTheCategoryAndNoGuide() {
        assertThat(get.execute(new GetSizeGuide.Query(ADMIN, women.id())))
            .isEqualTo(new GetSizeGuide.Result.Found(CategoryView.of(women), Optional.empty()));
    }

    @Test
    void givenAGuide_whenTheAdministratorSavesIt_thenItIsTheCategorysGuideInItsOrder() {
        var saved = edit.execute(new EditSizeGuide.Command(ADMIN, women.id(), WOMEN));

        var expected = new SizeGuideView(List.of("Busto (cm)", "Cintura (cm)"),
            List.of(new SizeGuideView.RowView("S", List.of("83–87", "65–69")),
                new SizeGuideView.RowView("M", List.of("88–93", "70–75"))),
            Optional.of("Medí sobre la ropa interior."), NOW);
        assertThat(saved).isEqualTo(new EditSizeGuide.Result.Saved(expected));
        assertThat(get.execute(new GetSizeGuide.Query(ADMIN, women.id())))
            .isEqualTo(new GetSizeGuide.Result.Found(CategoryView.of(women), Optional.of(expected)));
    }

    @Test
    void givenAGuide_whenAnInvalidOneIsSaved_thenItIsRefusedAndNothingChanges() {
        edit.execute(new EditSizeGuide.Command(ADMIN, women.id(), WOMEN));
        var lacking = new SizeGuideForm(List.of("Busto", "BUSTO"), List.of(new RowForm("S", List.of("83–87"))), null);

        assertThat(edit.execute(new EditSizeGuide.Command(ADMIN, women.id(), lacking)))
            .isEqualTo(new EditSizeGuide.Result.InvalidGuide(Set.of(SizeGuideForm.Field.COLUMNS,
                SizeGuideForm.Field.ROWS)));
        assertThat(guides.findByCategory(women.id()).orElseThrow().columns())
            .containsExactly("Busto (cm)", "Cintura (cm)");
    }

    @Test
    void givenAGuide_whenTheAdministratorDeletesIt_thenTheCategoryHasNone() {
        edit.execute(new EditSizeGuide.Command(ADMIN, women.id(), WOMEN));

        assertThat(delete.execute(new DeleteSizeGuide.Command(ADMIN, women.id())))
            .isEqualTo(new DeleteSizeGuide.Result.Deleted());
        assertThat(guides.findByCategory(women.id())).isEmpty();
        assertThat(delete.execute(new DeleteSizeGuide.Command(ADMIN, women.id())))
            .isEqualTo(new DeleteSizeGuide.Result.Deleted());
    }

    @Test
    void givenAnUnknownCategory_whenItsGuideIsOpenedSavedOrDeleted_thenItIsNotFound() {
        assertThat(get.execute(new GetSizeGuide.Query(ADMIN, unknown))).isEqualTo(new GetSizeGuide.Result.NotFound());
        assertThat(edit.execute(new EditSizeGuide.Command(ADMIN, unknown, WOMEN)))
            .isEqualTo(new EditSizeGuide.Result.NotFound());
        assertThat(delete.execute(new DeleteSizeGuide.Command(ADMIN, unknown)))
            .isEqualTo(new DeleteSizeGuide.Result.NotFound());
    }

    @Test
    void givenAnActorOtherThanTheAdministrator_whenItTouchesAGuide_thenItIsForbidden() {
        edit.execute(new EditSizeGuide.Command(ADMIN, women.id(), WOMEN));
        var other = new SizeGuideForm(List.of("Edad"), List.of(new RowForm("4", List.of("4 años"))), null);

        assertThat(get.execute(new GetSizeGuide.Query(VISITOR, women.id())))
            .isEqualTo(new GetSizeGuide.Result.Forbidden());
        assertThat(edit.execute(new EditSizeGuide.Command(VISITOR, women.id(), other)))
            .isEqualTo(new EditSizeGuide.Result.Forbidden());
        assertThat(delete.execute(new DeleteSizeGuide.Command(VISITOR, women.id())))
            .isEqualTo(new DeleteSizeGuide.Result.Forbidden());
        assertThat(guides.findByCategory(women.id()).orElseThrow().columns()).containsExactly("Busto (cm)",
            "Cintura (cm)");
    }

    private Category saved(String name) {
        var category = Category.create(CategoryId.of(UUID.randomUUID()), new CategoryName(name), categories.findAll(),
            NOW.minusSeconds(3600));
        categories.save(category);
        return category;
    }
}
