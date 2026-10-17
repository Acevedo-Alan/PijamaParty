package com.pijamaparty.store.accounts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** UC-20: an address of the book has the formats of the order's address (UC-13). */
class AddressFormTest {

    @Test
    void givenAnAddressWithSpacesAndALowerCaseCpa_whenValidated_thenItIsTrimmedAndNormalized() {
        var form = new AddressForm("  San Martín ", " 1234 ", " 3 ", "  ", " Santa Fe ", Province.SANTA_FE,
            " s3000abc ");

        assertThat(form.validate()).isEqualTo(new AddressForm.Outcome.Valid(new AddressDetails("San Martín", "1234",
            Optional.of("3"), Optional.empty(), "Santa Fe", Province.SANTA_FE, "S3000ABC")));
    }

    @Test
    void givenAFourDigitPostalCodeAndNoFloorOrApartment_whenValidated_thenItIsValid() {
        var form = new AddressForm("Rivadavia", "50", null, null, "Rosario", Province.SANTA_FE, "2000");

        assertThat(form.validate()).isInstanceOf(AddressForm.Outcome.Valid.class);
    }

    @Test
    void givenFieldsThatBreakTheRules_whenValidated_thenEachOneIsNamed() {
        var form = new AddressForm(" ", "12345678901", "12345678901", "12345678901", "x".repeat(81),
            Province.CORDOBA, "S3000");

        assertThat(form.validate()).isEqualTo(new AddressForm.Outcome.Invalid(Set.of(AddressForm.Field.values())));
    }

    @Test
    void givenTheLongestValues_whenValidated_thenTheyAreValidCountingCodePoints() {
        var form = new AddressForm("ñ".repeat(100), "😀".repeat(10), "1".repeat(10), "A".repeat(10), "c".repeat(80),
            Province.TUCUMAN, "T4000ABC");

        assertThat(form.validate()).isInstanceOf(AddressForm.Outcome.Valid.class);
    }

    @Test
    void givenAForm_whenPrinted_thenNoPersonalDataShows() {
        var form = new AddressForm("San Martín", "1234", null, null, "Santa Fe", Province.SANTA_FE, "S3000ABC");

        assertThat(form.toString()).doesNotContain("San Martín", "S3000ABC");
    }
}
