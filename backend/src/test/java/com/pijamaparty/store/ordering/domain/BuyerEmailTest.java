package com.pijamaparty.store.ordering.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** UC-13, UC-17: the rule of the buyer's email, in the checkout and in its correction (T-E3-23). */
class BuyerEmailTest {

    @Test
    void givenAnEmailWithSpacesAndCapitals_whenNormalized_thenItIsTrimmedAndInLowerCase() {
        assertThat(BuyerEmail.normalize("  Ana.Perez+Pijamas@Gmail.COM\t")).contains("ana.perez+pijamas@gmail.com");
    }

    @Test
    void givenAnEmailOf254Characters_whenNormalized_thenItIsValid() {
        String email = "a".repeat(244) + "@gmail.com";

        assertThat(BuyerEmail.normalize(email)).contains(email);
    }

    @Test
    void givenAnEmailOf255Characters_whenNormalized_thenItIsInvalid() {
        assertThat(BuyerEmail.normalize("a".repeat(245) + "@gmail.com")).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "ana", "ana perez@gmail.com", "@gmail.com", "ana@", "ana@gm@il.com",
        "anñ@gmail.com"})
    void givenNotAnEmail_whenNormalized_thenItIsInvalid(String typed) {
        assertThat(BuyerEmail.normalize(typed)).isEmpty();
    }
}
