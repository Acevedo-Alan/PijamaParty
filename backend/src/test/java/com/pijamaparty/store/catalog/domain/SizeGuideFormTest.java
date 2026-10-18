package com.pijamaparty.store.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.SizeGuideForm.Field;
import com.pijamaparty.store.catalog.domain.SizeGuideForm.Outcome;
import com.pijamaparty.store.catalog.domain.SizeGuideForm.RowForm;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** RNF-31.5: the rules of a size guide, as typed by the administrator. */
class SizeGuideFormTest {

    private static final CategoryId CATEGORY = CategoryId.of(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

    @Test
    void givenAGuideWithSpaces_whenValidated_thenItsTextsAreTrimmedAndKeepTheirOrder() {
        var form = new SizeGuideForm(List.of(" Busto (cm) ", "Cintura (cm)"),
            List.of(new RowForm(" M ", List.of(" 88–93 ", " ")), new RowForm("S", List.of("83–87", "65–69"))),
            "  Medí sobre la ropa interior.\nSi dudás, elegí el talle mayor.  ");

        assertThat(form.toGuide(CATEGORY, NOW)).isEqualTo(new Outcome.Valid(new SizeGuide(CATEGORY,
            List.of("Busto (cm)", "Cintura (cm)"),
            List.of(row("M", "88–93", ""), row("S", "83–87", "65–69")),
            Optional.of("Medí sobre la ropa interior.\nSi dudás, elegí el talle mayor."), NOW)));
    }

    @Test
    void givenABlankNote_whenValidated_thenTheGuideHasNone() {
        var form = new SizeGuideForm(List.of("Edad"), List.of(new RowForm("4", List.of("4 años"))), "   ");

        assertThat(valid(form).note()).isEmpty();
        assertThat(valid(new SizeGuideForm(List.of("Edad"), List.of(new RowForm("4", List.of("4"))), null)).note())
            .isEmpty();
    }

    @Test
    void givenARowThatLacksAValueForAColumn_whenValidated_thenTheRowsAreInvalid() {
        var form = new SizeGuideForm(List.of("Busto", "Cintura"),
            List.of(new RowForm("S", List.of("83–87", "65–69")), new RowForm("M", List.of("88–93"))), null);

        assertThat(form.toGuide(CATEGORY, NOW)).isEqualTo(new Outcome.Invalid(Set.of(Field.ROWS)));
    }

    @Test
    void givenTwoColumnsWithTheSameNameInDifferentCase_whenValidated_thenTheColumnsAreInvalid() {
        var form = new SizeGuideForm(List.of("Busto", " BUSTO"), List.of(new RowForm("S", List.of("1", "2"))), null);

        assertThat(form.toGuide(CATEGORY, NOW)).isEqualTo(new Outcome.Invalid(Set.of(Field.COLUMNS)));
    }

    @Test
    void givenTwoRowsWithTheSameSizeInDifferentCase_whenValidated_thenTheRowsAreInvalid() {
        var form = new SizeGuideForm(List.of("Busto"),
            List.of(new RowForm("xl", List.of("1")), new RowForm("XL", List.of("2"))), null);

        assertThat(form.toGuide(CATEGORY, NOW)).isEqualTo(new Outcome.Invalid(Set.of(Field.ROWS)));
    }

    @Test
    void givenColumnsOutOfTheirLimits_whenValidated_thenTheColumnsAreInvalid() {
        assertThat(invalid(List.of(), List.of())).contains(Field.COLUMNS);
        assertThat(invalid(names(7), List.of(new RowForm("S", names(7))))).containsExactly(Field.COLUMNS);
        assertThat(invalid(List.of("x".repeat(31)), List.of(new RowForm("S", List.of("1")))))
            .containsExactly(Field.COLUMNS);
        assertThat(invalid(List.of("  "), List.of(new RowForm("S", List.of("1"))))).containsExactly(Field.COLUMNS);
        assertThat(invalid(List.of("Busto\nCintura"), List.of(new RowForm("S", List.of("1")))))
            .containsExactly(Field.COLUMNS);
        assertThat(invalid(Arrays.asList("Busto", null), List.of(new RowForm("S", List.of("1", "2")))))
            .containsExactly(Field.COLUMNS);
        assertThat(invalid(null, List.of(new RowForm("S", List.of())))).containsExactly(Field.COLUMNS);
    }

    @Test
    void givenRowsOutOfTheirLimits_whenValidated_thenTheRowsAreInvalid() {
        List<String> one = List.of("Busto");
        assertThat(invalid(one, List.of())).containsExactly(Field.ROWS);
        assertThat(invalid(one, IntStream.rangeClosed(1, 21).mapToObj(i -> new RowForm("T" + i, List.of("1")))
            .toList())).containsExactly(Field.ROWS);
        assertThat(invalid(one, List.of(new RowForm("x".repeat(21), List.of("1"))))).containsExactly(Field.ROWS);
        assertThat(invalid(one, List.of(new RowForm(" ", List.of("1"))))).containsExactly(Field.ROWS);
        assertThat(invalid(one, List.of(new RowForm("S", List.of("9".repeat(21)))))).containsExactly(Field.ROWS);
        assertThat(invalid(one, List.of(new RowForm("S", List.of("1\t2"))))).containsExactly(Field.ROWS);
        assertThat(invalid(one, List.of(new RowForm("S", Collections.singletonList(null))))).containsExactly(Field.ROWS);
        assertThat(invalid(one, Collections.singletonList(null))).containsExactly(Field.ROWS);
        assertThat(invalid(one, null)).containsExactly(Field.ROWS);
    }

    @Test
    void givenTheLargestGuide_whenValidated_thenItIsValid() {
        List<RowForm> rows = IntStream.rangeClosed(1, 20)
            .mapToObj(i -> new RowForm(("T" + i + "x".repeat(20)).substring(0, 20), names(6).stream()
                .map(name -> "9".repeat(20)).toList()))
            .toList();
        var form = new SizeGuideForm(names(6).stream().map(name -> (name + "x".repeat(30)).substring(0, 30)).toList(),
            rows, "n".repeat(500));

        assertThat(valid(form).rows()).hasSize(20);
    }

    @Test
    void givenANoteTooLong_whenValidated_thenOnlyTheNoteIsInvalid() {
        var form = new SizeGuideForm(List.of("Busto"), List.of(new RowForm("S", List.of("1"))), "n".repeat(501));

        assertThat(form.toGuide(CATEGORY, NOW)).isEqualTo(new Outcome.Invalid(Set.of(Field.NOTE)));
    }

    private static SizeGuide valid(SizeGuideForm form) {
        return ((Outcome.Valid) form.toGuide(CATEGORY, NOW)).guide();
    }

    private static Set<Field> invalid(List<String> columns, List<RowForm> rows) {
        return ((Outcome.Invalid) new SizeGuideForm(columns, rows, null).toGuide(CATEGORY, NOW)).fields();
    }

    private static List<String> names(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> "Medida " + i).toList();
    }

    private static SizeGuide.Row row(String size, String... values) {
        return new SizeGuide.Row(new Size(size), List.of(values));
    }
}
