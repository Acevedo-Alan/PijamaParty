package com.pijamaparty.store.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CategoryNameAndSlugTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Pijamas de Niñas      | pijamas-de-ninas",
        "Camisones & Batas     | camisones-batas",
        "  --Invierno 2026!--  | invierno-2026",
        "ÁÉÍÓÚ Üñ              | aeiou-un",
        "Pijamas               | pijamas",
        "PIJAMAS               | pijamas"})
    void givenAName_whenItsSlugIsGenerated_thenItIsLowerCaseWithoutAccentsAndHyphenated(String name, String slug) {
        assertThat(CategoryName.parse(name).orElseThrow().slug()).isEqualTo(new Slug(slug));
    }

    @Test
    void givenANameWithSurroundingSpaces_whenParsed_thenItIsTrimmed() {
        assertThat(CategoryName.parse("  Verano  ")).contains(new CategoryName("Verano"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "¡¿?!", "—"})
    void givenANameWithoutLettersOrDigits_whenParsed_thenItIsRejected(String name) {
        assertThat(CategoryName.parse(name)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ver\u0000ano", "Ver\tano", "Ver\nano", "Ver\u200Bano", "Ver\u202Eano", "Ver\u2028ano"})
    void givenANameWithControlOrInvisibleCharacters_whenParsed_thenItIsRejected(String name) {
        assertThat(CategoryName.parse(name)).isEmpty();
    }

    @Test
    void givenANameOfMoreThan80Characters_whenParsed_thenItIsRejected() {
        assertThat(CategoryName.parse("a".repeat(80))).isPresent();
        assertThat(CategoryName.parse("a".repeat(81))).isEmpty();
        assertThat(CategoryName.parse(null)).isEmpty();
    }

    @Test
    void givenAShortNameWhoseSlugWouldExceed100Characters_whenParsed_thenItIsRejected() {
        // U+3392 (square MHz) is one character that becomes three letters under compatibility decomposition.
        assertThat(CategoryName.parse("\u3392".repeat(34))).isEmpty();
        assertThat(CategoryName.parse("\u3392".repeat(33))).isPresent();
    }

    @Test
    void givenAValueOutsideTheDatabaseFormat_whenBuildingASlug_thenItIsRejected() {
        assertThatThrownBy(() -> new Slug("Pijamas")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Slug("pijamas--largos")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Slug("-pijamas")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CategoryName(" Verano")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenTextFromAVisitor_whenParsedAsASlug_thenOnlyTheDatabaseFormatIsAccepted() {
        assertThat(Slug.parse("pijama-rosa")).contains(new Slug("pijama-rosa"));
        assertThat(Slug.parse("x".repeat(Slug.MAX_LENGTH))).isPresent();
        assertThat(Slug.parse("x".repeat(Slug.MAX_LENGTH + 1))).isEmpty();
        assertThat(Slug.parse("Pijama")).isEmpty();
        assertThat(Slug.parse("pijama--rosa")).isEmpty();
        assertThat(Slug.parse("")).isEmpty();
    }
}
