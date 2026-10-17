package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-28 (T-E4-16): coming back from a provider confirms the deletion only with the account linked to the customer. */
class ReauthenticateWithExternalAccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T15:00:00Z");
    private static final ExternalAccount ANA_GOOGLE =
        new ExternalAccount(ExternalProvider.GOOGLE, "109876543210987654321");
    private static final ExternalAccount BETO_GOOGLE =
        new ExternalAccount(ExternalProvider.GOOGLE, "200000000000000000001");
    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final UserId BETO = UserId.of(UUID.randomUUID());

    private final InMemoryExternalIdentities identities = new InMemoryExternalIdentities();
    private final InMemoryExternalSignIns signIns = new InMemoryExternalSignIns();
    private final ReauthenticateWithExternalAccountService reauthenticate =
        new ReauthenticateWithExternalAccountService(signIns, identities);

    @Test
    void givenTheExternalAccountLinkedToTheCustomer_whenReauthenticating_thenItIsConfirmedInOneTransaction() {
        link(ANA, ANA_GOOGLE);

        var result = reauthenticate.execute(new ReauthenticateWithExternalAccount.Command(ANA, ANA_GOOGLE));

        assertThat(result).isEqualTo(new ReauthenticateWithExternalAccount.Result.Reauthenticated());
        assertThat(signIns.transactions()).isOne();
    }

    @Test
    void givenAnotherCustomersExternalAccount_whenReauthenticating_thenItIsNotLinked() {
        link(ANA, ANA_GOOGLE);
        link(BETO, BETO_GOOGLE);

        assertThat(reauthenticate.execute(new ReauthenticateWithExternalAccount.Command(ANA, BETO_GOOGLE)))
            .isEqualTo(new ReauthenticateWithExternalAccount.Result.NotLinked());
    }

    @Test
    void givenAnExternalAccountLinkedToNobody_whenReauthenticating_thenItIsNotLinkedAndNothingIsLinked() {
        assertThat(reauthenticate.execute(new ReauthenticateWithExternalAccount.Command(ANA, ANA_GOOGLE)))
            .isEqualTo(new ReauthenticateWithExternalAccount.Result.NotLinked());
        assertThat(identities.all()).isEmpty();
    }

    private void link(UserId customer, ExternalAccount account) {
        identities.link(ExternalIdentity.link(UUID.randomUUID(), customer, account, new Email("x@example.com"), NOW));
    }
}
