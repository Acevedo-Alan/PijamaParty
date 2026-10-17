package com.pijamaparty.store.accounts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-27 (T-E4-15): the last way in cannot be unlinked. */
class SignInMethodsTest {

    private static final Instant NOW = Instant.parse("2026-10-17T15:00:00Z");
    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final ExternalIdentity GOOGLE = identity(ANA, ExternalProvider.GOOGLE);
    private static final ExternalIdentity FACEBOOK = identity(ANA, ExternalProvider.FACEBOOK);

    @Test
    void givenOnlyGoogle_whenUnlinkingIt_thenItIsTheLastSignInMethod() {
        var methods = new SignInMethods(ANA, false, List.of(GOOGLE));

        assertThat(methods.unlink(ExternalProvider.GOOGLE))
            .isEqualTo(new SignInMethods.UnlinkOutcome.LastSignInMethod());
    }

    @Test
    void givenGoogleAndAPassword_whenUnlinkingGoogle_thenItIsUnlinked() {
        var methods = new SignInMethods(ANA, true, List.of(GOOGLE));

        assertThat(methods.unlink(ExternalProvider.GOOGLE)).isEqualTo(new SignInMethods.UnlinkOutcome.Unlinked(GOOGLE));
    }

    @Test
    void givenBothProvidersWithoutAPassword_whenUnlinkingOne_thenItIsUnlinked() {
        var methods = new SignInMethods(ANA, false, List.of(GOOGLE, FACEBOOK));

        assertThat(methods.unlink(ExternalProvider.FACEBOOK))
            .isEqualTo(new SignInMethods.UnlinkOutcome.Unlinked(FACEBOOK));
    }

    @Test
    void givenNoAccountOfAProvider_whenUnlinkingIt_thenItIsNotLinked() {
        var methods = new SignInMethods(ANA, true, List.of(GOOGLE));

        assertThat(methods.unlink(ExternalProvider.FACEBOOK)).isEqualTo(new SignInMethods.UnlinkOutcome.NotLinked());
    }

    @Test
    void givenTheIdentitiesInAnyOrder_thenGoogleComesFirst() {
        var methods = new SignInMethods(ANA, false, List.of(FACEBOOK, GOOGLE));

        assertThat(methods.identities()).containsExactly(GOOGLE, FACEBOOK);
        assertThat(methods.identity(ExternalProvider.FACEBOOK)).contains(FACEBOOK);
    }

    @Test
    void givenAnotherCustomersIdentity_thenItIsRefused() {
        ExternalIdentity beto = identity(UserId.of(UUID.randomUUID()), ExternalProvider.GOOGLE);

        assertThatThrownBy(() -> new SignInMethods(ANA, true, List.of(beto)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenTwoAccountsOfOneProvider_thenItIsRefused() {
        ExternalIdentity another = ExternalIdentity.link(UUID.randomUUID(), ANA,
            new ExternalAccount(ExternalProvider.GOOGLE, "200000000000000000001"), new Email("ana@example.com"), NOW);

        assertThatThrownBy(() -> new SignInMethods(ANA, true, List.of(GOOGLE, another)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static ExternalIdentity identity(UserId user, ExternalProvider provider) {
        return ExternalIdentity.link(UUID.randomUUID(), user,
            new ExternalAccount(provider, provider == ExternalProvider.GOOGLE ? "109876543210987654321" : "1022987"),
            new Email("ana@example.com"), NOW);
    }
}
