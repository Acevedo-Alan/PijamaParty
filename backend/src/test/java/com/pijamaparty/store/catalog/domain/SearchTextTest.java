package com.pijamaparty.store.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SearchTextTest {

    @Test
    void givenATextWithSpaces_whenParsed_thenItIsTrimmed() {
        assertThat(SearchText.parse("  pijama de algodón ")).contains(new SearchText("pijama de algodón"));
    }

    @Test
    void givenFewerThan2Characters_whenParsed_thenItIsNotASearch() {
        assertThat(SearchText.parse("")).isEmpty();
        assertThat(SearchText.parse(" a ")).isEmpty();
        assertThat(SearchText.parse("\u0000a​")).isEmpty();
        assertThat(SearchText.parse("ab")).contains(new SearchText("ab"));
    }

    @Test
    void givenMoreThan100Characters_whenParsed_thenItIsCutTo100() {
        Optional<SearchText> text = SearchText.parse("a".repeat(99) + " " + "b".repeat(20));

        assertThat(text).contains(new SearchText("a".repeat(99)));
        assertThat(SearchText.parse("ñ".repeat(150)).orElseThrow().value()).hasSize(100);
    }

    @Test
    void givenControlAndInvisibleCharacters_whenParsed_thenTheyBecomeSpaces() {
        assertThat(SearchText.parse("pijama\u0000rosa‮azul\ttalle")).contains(
            new SearchText("pijama rosa azul talle"));
    }

    @Test
    void givenQuotesSymbolsAndOperators_whenSplitIntoWords_thenOnlyLettersAndDigitsRemain() {
        assertThat(new SearchText("'; DROP TABLE product; -- \"pijama\" & !rosa:* talle 10").words())
            .containsExactly("DROP", "TABLE", "product", "pijama", "rosa", "talle", "10");
        assertThat(new SearchText("algodón niño").words()).containsExactly("algodón", "niño");
        assertThat(new SearchText("&&| !:*").words()).isEmpty();
    }

    @Test
    void givenAnInvalidValue_whenBuilt_thenItIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SearchText("a"));
        assertThatIllegalArgumentException().isThrownBy(() -> new SearchText(" ab"));
        assertThatIllegalArgumentException().isThrownBy(() -> new SearchText("a".repeat(101)));
        assertThatIllegalArgumentException().isThrownBy(() -> new SearchText("a\u0000b"));
    }
}
