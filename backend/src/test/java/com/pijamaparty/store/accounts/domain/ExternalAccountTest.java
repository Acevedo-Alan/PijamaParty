package com.pijamaparty.store.accounts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** UC-27: an account at a provider, as the provider identifies it. */
class ExternalAccountTest {

    @Test
    void givenWhatAProviderReturned_whenItIsAnIdentifier_thenItIsAnExternalAccount() {
        assertThat(ExternalAccount.of(ExternalProvider.GOOGLE, "109876543210987654321")).isPresent();
        assertThat(ExternalAccount.of(ExternalProvider.FACEBOOK, "x".repeat(255))).isPresent();
    }

    @Test
    void givenWhatAProviderReturned_whenItCannotBeAnIdentifier_thenThereIsNone() {
        assertThat(ExternalAccount.of(ExternalProvider.GOOGLE, null)).isEmpty();
        assertThat(ExternalAccount.of(ExternalProvider.GOOGLE, "")).isEmpty();
        assertThat(ExternalAccount.of(ExternalProvider.GOOGLE, "has space")).isEmpty();
        assertThat(ExternalAccount.of(ExternalProvider.GOOGLE, "ñandú")).isEmpty();
        assertThat(ExternalAccount.of(ExternalProvider.GOOGLE, "x".repeat(256))).isEmpty();
        assertThatThrownBy(() -> new ExternalAccount(ExternalProvider.GOOGLE, "has space"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenAnExternalAccount_whenPrinted_thenItsIdentifierIsHidden() {
        assertThat(new ExternalAccount(ExternalProvider.FACEBOOK, "10229876543210").toString())
            .doesNotContain("10229876543210")
            .contains("FACEBOOK");
    }

    @Test
    void givenEachProvider_whenAskedWhetherItVouchesForEmails_thenOnlyGoogleDoes() {
        assertThat(ExternalProvider.GOOGLE.vouchesForEmails()).isTrue();
        assertThat(ExternalProvider.FACEBOOK.vouchesForEmails()).isFalse();
    }
}
