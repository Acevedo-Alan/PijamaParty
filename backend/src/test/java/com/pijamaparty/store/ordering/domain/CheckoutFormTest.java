package com.pijamaparty.store.ordering.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.domain.CheckoutForm.Field;
import com.pijamaparty.store.ordering.domain.CheckoutForm.Outcome;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** UC-13: the buyer data and the address, normalized and validated by the domain (RNF-28). */
class CheckoutFormTest {

    private static final CheckoutForm VALID = new CheckoutForm("Ana", "Pérez", "ana@example.com", "342 555-1234",
        "12.345.678", "San Martín", "1234", null, null, "Santa Fe", "3000");

    @Test
    void givenDataAsTyped_whenValidated_thenItIsTrimmedAndNormalized() {
        var form = new CheckoutForm("  Ana ", " Pérez  ", " Ana@Example.COM ", "+54 (342) 555-1234", "12.345.678",
            " San Martín ", " 1234 ", " 3 ", " B ", " Santa Fe ", " s3000abc ");

        assertThat(form.validate()).isEqualTo(new Outcome.Valid(
            new BuyerDetails("Ana", "Pérez", "ana@example.com", "543425551234", "12345678"),
            new ShippingAddress("San Martín", "1234", Optional.of("3"), Optional.of("B"), "Santa Fe", "S3000ABC")));
    }

    @Test
    void givenACompoundFirstName_whenValidated_thenItIsKeptAsTyped() {
        var form = new CheckoutForm("María José", "Pérez Gómez", "ana@example.com", "3425551234", "12345678",
            "San Martín", "1234", null, null, "Santa Fe", "3000");

        var valid = (Outcome.Valid) form.validate();

        assertThat(valid.buyer().firstName()).isEqualTo("María José");
        assertThat(valid.buyer().lastName()).isEqualTo("Pérez Gómez");
    }

    @Test
    void givenBlankFloorAndApartment_whenValidated_thenTheAddressHasNone() {
        var form = new CheckoutForm("Ana", "Pérez", "ana@example.com", "3425551234", "1234567", "Rivadavia", "10",
            "  ", "", "Rosario", "2000");

        var valid = (Outcome.Valid) form.validate();

        assertThat(valid.address().floor()).isEmpty();
        assertThat(valid.address().apartment()).isEmpty();
        assertThat(valid.buyer().nationalId()).isEqualTo("1234567");
    }

    @Test
    void givenEveryRequiredFieldEmpty_whenValidated_thenEachIsNamed() {
        var form = new CheckoutForm(" ", null, "", null, "", "", " ", null, null, "", "");

        assertThat(form.validate()).isEqualTo(new Outcome.Invalid(Set.of(Field.FIRST_NAME, Field.LAST_NAME,
            Field.EMAIL, Field.PHONE, Field.NATIONAL_ID, Field.STREET, Field.NUMBER, Field.CITY, Field.POSTAL_CODE)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456", "123456789", "12-345-678", "12345678A", "DNI 12345678"})
    void givenANationalIdWithAnInvalidFormat_whenValidated_thenItIsRejected(String nationalId) {
        assertThat(with(nationalId, Field.NATIONAL_ID).validate()).isEqualTo(new Outcome.Invalid(Set.of(
            Field.NATIONAL_ID)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567", "1234567890123456", "342 555 12x4", "342.555.1234"})
    void givenAPhoneWithFewerThan8OrMoreThan15DigitsOrOtherCharacters_whenValidated_thenItIsRejected(String phone) {
        assertThat(with(phone, Field.PHONE).validate()).isEqualTo(new Outcome.Invalid(Set.of(Field.PHONE)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana", "@example.com", "ana@", "ana @example.com", "ana@ex@ample.com", "añá@example.com"})
    void givenAnEmailThatIsNotOne_whenValidated_thenItIsRejected(String email) {
        assertThat(with(email, Field.EMAIL).validate()).isEqualTo(new Outcome.Invalid(Set.of(Field.EMAIL)));
    }

    @Test
    void givenAnEmailOf254Characters_whenValidated_thenItIsAcceptedAnd255IsNot() {
        String domain = "@example.com";
        String longest = "a".repeat(254 - domain.length()) + domain;

        assertThat(with(longest, Field.EMAIL).validate()).isInstanceOf(Outcome.Valid.class);
        assertThat(with("a" + longest, Field.EMAIL).validate()).isEqualTo(new Outcome.Invalid(Set.of(Field.EMAIL)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"300", "30000", "S3000AB", "3000ABC", "SS3000ABC"})
    void givenAPostalCodeThatIsNeither4DigitsNorACpa_whenValidated_thenItIsRejected(String postalCode) {
        assertThat(with(postalCode, Field.POSTAL_CODE).validate())
            .isEqualTo(new Outcome.Invalid(Set.of(Field.POSTAL_CODE)));
    }

    @Test
    void givenTextsAtTheirLimits_whenValidated_thenTheyAreAcceptedAndOneMoreCharacterIsNot() {
        var atLimits = new CheckoutForm("n".repeat(50), "l".repeat(50), "ana@example.com", "3425551234", "12345678",
            "s".repeat(100), "1".repeat(10), "f".repeat(10), "a".repeat(10), "c".repeat(80), "3000");
        var overLimits = new CheckoutForm("n".repeat(51), "l".repeat(51), "ana@example.com", "3425551234",
            "12345678", "s".repeat(101), "1".repeat(11), "f".repeat(11), "a".repeat(11), "c".repeat(81), "3000");

        assertThat(atLimits.validate()).isInstanceOf(Outcome.Valid.class);
        assertThat(overLimits.validate()).isEqualTo(new Outcome.Invalid(Set.of(Field.FIRST_NAME, Field.LAST_NAME,
            Field.STREET, Field.NUMBER, Field.FLOOR, Field.APARTMENT, Field.CITY)));
    }

    @Test
    void givenPersonalData_whenPrinted_thenNoneOfItShows() {
        var valid = (Outcome.Valid) VALID.validate();

        assertThat(VALID.toString()).doesNotContain("Ana", "Pérez", "12.345.678", "San Martín");
        assertThat(valid.buyer().toString()).doesNotContain("Ana", "Pérez", "12345678", "example");
        assertThat(valid.address().toString()).doesNotContain("San Martín", "1234", "3000");
    }

    /** {@link #VALID} with {@code value} in {@code field}. */
    private static CheckoutForm with(String value, Field field) {
        return new CheckoutForm(
            field == Field.FIRST_NAME ? value : VALID.firstName(),
            field == Field.LAST_NAME ? value : VALID.lastName(),
            field == Field.EMAIL ? value : VALID.email(),
            field == Field.PHONE ? value : VALID.phone(),
            field == Field.NATIONAL_ID ? value : VALID.nationalId(),
            VALID.street(), VALID.number(), VALID.floor(), VALID.apartment(), VALID.city(),
            field == Field.POSTAL_CODE ? value : VALID.postalCode());
    }
}
