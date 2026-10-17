package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.application.LinkExternalAccount.Command;
import com.pijamaparty.store.accounts.application.LinkExternalAccount.Result;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.application.InMemoryIdGenerator;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-27 (T-E4-15): a signed-in customer links Google or Facebook from "Mi cuenta". */
class LinkExternalAccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T15:00:00Z");
    private static final ExternalAccount GOOGLE = new ExternalAccount(ExternalProvider.GOOGLE, "109876543210987654321");
    private static final ExternalAccount FACEBOOK = new ExternalAccount(ExternalProvider.FACEBOOK, "10229876543210");
    private static final Email ANA = new Email("ana@example.com");

    private final InMemoryExternalSignIns signIns = new InMemoryExternalSignIns();
    private final InMemoryExternalIdentities identities = new InMemoryExternalIdentities();
    private final InMemoryUserStore users = new InMemoryUserStore();
    private final InMemoryPasswordHasher passwords = new InMemoryPasswordHasher();
    private final LinkExternalAccountService service = new LinkExternalAccountService(signIns, identities,
        identities, users, users, new InMemoryIdGenerator(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void givenAFacebookAccountWithAnotherEmail_whenLinkingIt_thenItIsLinkedWithThatEmailAndNothingElseChanges() {
        User ana = verifiedCustomerWithPassword();

        Result result = service.execute(new Command(ana.id(), FACEBOOK, Optional.of(" Ana.P@Example.com "), false));

        assertThat(result).isEqualTo(new Result.Linked());
        assertThat(identities.find(FACEBOOK)).get().satisfies(identity -> {
            assertThat(identity.userId()).isEqualTo(ana.id());
            assertThat(identity.email()).isEqualTo(new Email("ana.p@example.com"));
        });
        assertThat(users.findById(ana.id()).orElseThrow().passwordHash()).isPresent();
        assertThat(signIns.transactions()).isOne();
    }

    @Test
    void givenAProviderWithoutEmail_whenLinkingIt_thenItKeepsTheAccountsEmail() {
        User ana = verifiedCustomerWithPassword();

        assertThat(service.execute(new Command(ana.id(), FACEBOOK, Optional.empty(), false)))
            .isEqualTo(new Result.Linked());

        assertThat(identities.find(FACEBOOK).orElseThrow().email()).isEqualTo(ANA);
    }

    @Test
    void givenAnUnverifiedCustomer_whenLinkingGoogleVouchingForItsEmail_thenItIsVerifiedAndLosesItsPassword() {
        User ana = User.newCustomer(UserId.of(UUID.randomUUID()), ANA, "Ana", passwords.hash("una clave"), NOW);
        users.save(ana);

        assertThat(service.execute(new Command(ana.id(), GOOGLE, Optional.of("ana@example.com"), true)))
            .isEqualTo(new Result.Linked());

        User linked = users.findById(ana.id()).orElseThrow();
        assertThat(linked.emailVerified()).isTrue();
        assertThat(linked.passwordHash()).isEmpty();
    }

    @Test
    void givenAnUnverifiedCustomer_whenLinkingGoogleWithAnotherEmail_thenItStaysUnverifiedWithItsPassword() {
        User ana = User.newCustomer(UserId.of(UUID.randomUUID()), ANA, "Ana", passwords.hash("una clave"), NOW);
        users.save(ana);

        assertThat(service.execute(new Command(ana.id(), GOOGLE, Optional.of("otra@example.com"), true)))
            .isEqualTo(new Result.Linked());

        User linked = users.findById(ana.id()).orElseThrow();
        assertThat(linked.emailVerified()).isFalse();
        assertThat(linked.passwordHash()).isPresent();
    }

    @Test
    void givenTheExternalAccountAlreadyLinkedToThisCustomer_whenLinkingItAgain_thenItIsLinkedWithNothingNew() {
        User ana = verifiedCustomerWithPassword();
        identities.link(ExternalIdentity.link(UUID.randomUUID(), ana.id(), GOOGLE, ANA, NOW));

        assertThat(service.execute(new Command(ana.id(), GOOGLE, Optional.of("ana@example.com"), true)))
            .isEqualTo(new Result.Linked());

        assertThat(identities.all()).hasSize(1);
    }

    @Test
    void givenTheExternalAccountOfAnotherCustomer_whenLinkingIt_thenNothingIsLinked() {
        User ana = verifiedCustomerWithPassword();
        User beto = User.newExternalCustomer(UserId.of(UUID.randomUUID()), new Email("beto@example.com"), "Beto", true,
            NOW);
        users.save(beto);
        identities.link(ExternalIdentity.link(UUID.randomUUID(), beto.id(), GOOGLE, beto.email(), NOW));

        assertThat(service.execute(new Command(ana.id(), GOOGLE, Optional.of("beto@example.com"), true)))
            .isEqualTo(new Result.AlreadyLinked());

        assertThat(identities.find(GOOGLE).orElseThrow().userId()).isEqualTo(beto.id());
    }

    @Test
    void givenACustomerWithAnotherAccountOfThatProvider_whenLinkingOne_thenNothingIsLinked() {
        User ana = verifiedCustomerWithPassword();
        identities.link(ExternalIdentity.link(UUID.randomUUID(), ana.id(), GOOGLE, ANA, NOW));
        var anotherGoogle = new ExternalAccount(ExternalProvider.GOOGLE, "200000000000000000001");

        assertThat(service.execute(new Command(ana.id(), anotherGoogle, Optional.of("ana@example.com"), true)))
            .isEqualTo(new Result.AlreadyLinked());

        assertThat(identities.find(anotherGoogle)).isEmpty();
    }

    @Test
    void givenADeletedAccount_whenLinking_thenItIsNotFound() {
        assertThat(service.execute(new Command(UserId.of(UUID.randomUUID()), GOOGLE, Optional.empty(), true)))
            .isEqualTo(new Result.NotFound());

        assertThat(identities.all()).isEmpty();
    }

    private User verifiedCustomerWithPassword() {
        User ana = User.newCustomer(UserId.of(UUID.randomUUID()), ANA, "Ana", passwords.hash("una clave"), NOW);
        ana.verifyEmail(NOW);
        users.save(ana);
        return ana;
    }
}
