package com.pijamaparty.store.accounts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailAndPasswordRulesTest {

    @Test
    void givenAnEmailWithSpacesAndCapitals_whenParsed_thenItIsNormalizedSoSignInIgnoresCase() {
        assertThat(Email.parse("  Owner@Example.COM ")).map(Email::value).contains("owner@example.com");
    }

    @Test
    void givenTextThatIsNotAnEmail_whenParsed_thenItIsEmpty() {
        assertThat(Email.parse(null)).isEmpty();
        assertThat(Email.parse("owner")).isEmpty();
        assertThat(Email.parse("@example.com")).isEmpty();
        assertThat(Email.parse("owner@")).isEmpty();
        assertThat(Email.parse("a@b@c")).isEmpty();
        assertThat(Email.parse("own er@example.com")).isEmpty();
        assertThat(Email.parse("a".repeat(250) + "@b.ar")).isEmpty();
    }

    @Test
    void givenAnEmail_whenPrinted_thenItIsNotRevealed() {
        assertThat(Email.parse("owner@example.com").orElseThrow()).hasToString("Email[***]");
        assertThat(new PasswordHash("{bcrypt}secret")).hasToString("PasswordHash[***]");
    }

    @Test
    void givenPasswords_whenChecked_thenTheyNeed12CharactersAndAtMost72Bytes() {
        assertThat(PasswordRules.check("short-pass1")).contains(PasswordRules.Violation.TOO_SHORT);
        assertThat(PasswordRules.check(null)).contains(PasswordRules.Violation.TOO_SHORT);
        assertThat(PasswordRules.check("twelve-chars")).isEmpty();
        assertThat(PasswordRules.check("x".repeat(72))).isEmpty();
        assertThat(PasswordRules.check("x".repeat(73))).contains(PasswordRules.Violation.TOO_LONG);
        // 24 characters of 4 bytes each: 96 bytes
        assertThat(PasswordRules.check("😀".repeat(24))).contains(PasswordRules.Violation.TOO_LONG);
    }

    @Test
    void givenCustomerPasswords_whenChecked_thenTheyNeed8CharactersAndAtMost72Bytes() {
        assertThat(PasswordRules.checkCustomer("siete77")).contains(PasswordRules.Violation.TOO_SHORT);
        assertThat(PasswordRules.checkCustomer(null)).contains(PasswordRules.Violation.TOO_SHORT);
        assertThat(PasswordRules.checkCustomer("ocho1234")).isEmpty();
        assertThat(PasswordRules.checkCustomer("x".repeat(72))).isEmpty();
        assertThat(PasswordRules.checkCustomer("x".repeat(73))).contains(PasswordRules.Violation.TOO_LONG);
    }
}
