package com.pijamaparty.store.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** UC-06 and 00-dominio-y-reglas.md, maximum lengths: the texts of a product and its variants. */
class ProductTextsTest {

    @Test
    void givenAProductName_whenItsSlugIsGenerated_thenItFollowsTheCategoryRules() {
        assertThat(ProductName.parse("  Pijama de Algodón ").orElseThrow())
            .satisfies(name -> assertThat(name.value()).isEqualTo("Pijama de Algodón"))
            .satisfies(name -> assertThat(name.slug()).isEqualTo(new Slug("pijama-de-algodon")));
    }

    @Test
    void givenAProductNameOfMoreThan120Characters_whenParsed_thenItIsRejected() {
        assertThat(ProductName.parse("a".repeat(120))).isPresent();
        assertThat(ProductName.parse("a".repeat(121))).isEmpty();
        assertThat(ProductName.parse("¡¿?!")).isEmpty();
        assertThat(ProductName.parse("Pijama\ncorto")).isEmpty();
        assertThat(ProductName.parse(null)).isEmpty();
    }

    @Test
    void givenADescription_whenParsed_thenItIsTrimmedKeepsLineBreaksAndMayBeEmpty() {
        assertThat(Description.parse("  Suave.\r\nFresco.\n ")).contains(new Description("Suave.\r\nFresco."));
        assertThat(Description.parse("   ")).contains(new Description(""));
        assertThat(Description.parse("a".repeat(5000))).isPresent();
        assertThat(Description.parse("a".repeat(5001))).isEmpty();
        assertThat(Description.parse("Sua\u0000ve")).isEmpty();
        assertThat(Description.parse("Sua​ve")).isEmpty();
        assertThat(Description.parse(null)).isEmpty();
    }

    @Test
    void givenAMaterial_whenParsed_thenItIsOneTrimmedLineOf1To80Characters() {
        assertThat(Material.parse(" Algodón ")).contains(new Material("Algodón"));
        assertThat(Material.parse("a".repeat(80))).isPresent();
        assertThat(Material.parse("a".repeat(81))).isEmpty();
        assertThat(Material.parse("")).isEmpty();
        assertThat(Material.parse("Algo\tdón")).isEmpty();
    }

    @Test
    void givenSizesAndColors_whenParsed_thenTheyAreTrimmedAndBounded() {
        assertThat(Size.parse(" XL ")).contains(new Size("XL"));
        assertThat(Size.parse("a".repeat(20))).isPresent();
        assertThat(Size.parse("a".repeat(21))).isEmpty();
        assertThat(Color.parse(" Rosa viejo ")).contains(new Color("Rosa viejo"));
        assertThat(Color.parse("a".repeat(30))).isPresent();
        assertThat(Color.parse("a".repeat(31))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "M\u0000", "M‮", "M\nL"})
    void givenABlankOrHiddenSize_whenParsed_thenItIsRejected(String size) {
        assertThat(Size.parse(size)).isEmpty();
        assertThat(Color.parse(size)).isEmpty();
    }

    @Test
    void givenAValueOutsideTheDatabaseFormat_whenBuildingASku_thenItIsRejected() {
        assertThat(new Sku("PP-7K3M9QX2").value()).isEqualTo("PP-7K3M9QX2");
        assertThatThrownBy(() -> new Sku(" PP-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Sku("P".repeat(41))).isInstanceOf(IllegalArgumentException.class);
    }
}
