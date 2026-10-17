package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.accounts.domain.PasswordRules;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-27 (T-E4-15): "Mi cuenta" lists, unlinks and creates a password, as the signed-in customer. */
class SignInMethodsServicesTest {

    private static final Instant NOW = Instant.parse("2026-10-17T15:00:00Z");
    private static final ExternalAccount GOOGLE = new ExternalAccount(ExternalProvider.GOOGLE, "109876543210987654321");
    private static final ExternalAccount FACEBOOK = new ExternalAccount(ExternalProvider.FACEBOOK, "10229876543210");
    private static final Email ANA = new Email("ana@example.com");

    private final InMemoryUserStore users = new InMemoryUserStore();
    private final InMemoryExternalIdentities identities = new InMemoryExternalIdentities();
    private final InMemorySignInMethods methods = new InMemorySignInMethods(users, identities);
    private final InMemoryCustomerAccountChanges changes = new InMemoryCustomerAccountChanges();
    private final InMemoryPasswordHasher passwords = new InMemoryPasswordHasher();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ListSignInMethodsService list = new ListSignInMethodsService(methods);
    private final UnlinkExternalAccountService unlink = new UnlinkExternalAccountService(methods, methods);
    private final CreatePasswordService createPassword = new CreatePasswordService(changes, methods, users, users,
        passwords, clock);

    @Test
    void givenACustomerWithBothProviders_whenListing_thenItHasNoPasswordAndGoogleFirst() {
        User ana = customerWithoutPassword();
        link(ana, FACEBOOK);
        link(ana, GOOGLE);

        var result = list.execute(actor(ana));

        assertThat(result).isInstanceOfSatisfying(ListSignInMethods.Result.Listed.class, listed -> {
            assertThat(listed.methods().hasPassword()).isFalse();
            assertThat(listed.methods().identities())
                .extracting(SignInMethodsView.Identity::provider, SignInMethodsView.Identity::email)
                .containsExactly(tuple(ExternalProvider.GOOGLE, "ana@example.com"),
                    tuple(ExternalProvider.FACEBOOK, "ana@example.com"));
        });
    }

    @Test
    void givenAGuest_whenListing_thenItIsNotSignedIn() {
        assertThat(list.execute(Actor.anonymous(Optional.empty(), Optional.empty())))
            .isEqualTo(new ListSignInMethods.Result.NotSignedIn());
    }

    @Test
    void givenACustomerWhoseOnlyMethodIsGoogle_whenUnlinkingIt_thenItIsRefusedAndStaysLinked() {
        User ana = customerWithoutPassword();
        link(ana, GOOGLE);

        var result = unlink.execute(new UnlinkExternalAccount.Command(actor(ana), ExternalProvider.GOOGLE));

        assertThat(result).isEqualTo(new UnlinkExternalAccount.Result.LastSignInMethod());
        assertThat(identities.find(GOOGLE)).isPresent();
    }

    @Test
    void givenACustomerWithBothProviders_whenUnlinkingFacebook_thenOnlyGoogleRemains() {
        User ana = customerWithoutPassword();
        link(ana, GOOGLE);
        link(ana, FACEBOOK);

        var result = unlink.execute(new UnlinkExternalAccount.Command(actor(ana), ExternalProvider.FACEBOOK));

        assertThat(result).isEqualTo(new UnlinkExternalAccount.Result.Unlinked());
        assertThat(identities.all()).extracting(ExternalIdentity::account).containsExactly(GOOGLE);
    }

    @Test
    void givenAnUnverifiedCustomerWithAPassword_whenUnlinkingItsOnlyProvider_thenThePasswordCountsAndItIsUnlinked() {
        User ana = User.newCustomer(UserId.of(UUID.randomUUID()), ANA, "Ana", passwords.hash("una clave"), NOW);
        users.save(ana);
        link(ana, FACEBOOK);

        var result = unlink.execute(new UnlinkExternalAccount.Command(actor(ana), ExternalProvider.FACEBOOK));

        assertThat(result).isEqualTo(new UnlinkExternalAccount.Result.Unlinked());
        assertThat(identities.all()).isEmpty();
    }

    @Test
    void givenNoAccountOfThatProvider_whenUnlinkingIt_thenItIsNotLinked() {
        User ana = customerWithoutPassword();
        link(ana, GOOGLE);

        assertThat(unlink.execute(new UnlinkExternalAccount.Command(actor(ana), ExternalProvider.FACEBOOK)))
            .isEqualTo(new UnlinkExternalAccount.Result.NotLinked());
    }

    @Test
    void givenACustomerWithoutAPassword_whenCreatingOne_thenItSignsInWithItAndCanUnlinkGoogle() {
        User ana = customerWithoutPassword();
        link(ana, GOOGLE);

        var result = createPassword.execute(new CreatePassword.Command(actor(ana), "una clave larga"));

        assertThat(result).isEqualTo(new CreatePassword.Result.Created());
        assertThat(users.findById(ana.id()).orElseThrow().passwordHash()).contains(passwords.hash("una clave larga"));
        assertThat(changes.transactions()).isOne();
        assertThat(unlink.execute(new UnlinkExternalAccount.Command(actor(ana), ExternalProvider.GOOGLE)))
            .isEqualTo(new UnlinkExternalAccount.Result.Unlinked());
    }

    @Test
    void givenACustomerWithAPassword_whenCreatingAnother_thenItIsRefusedAndTheOldOneStays() {
        User ana = User.newCustomer(UserId.of(UUID.randomUUID()), ANA, "Ana", passwords.hash("la de antes"), NOW);
        users.save(ana);

        var result = createPassword.execute(new CreatePassword.Command(actor(ana), "una clave larga"));

        assertThat(result).isEqualTo(new CreatePassword.Result.AlreadySet());
        assertThat(users.findById(ana.id()).orElseThrow().passwordHash()).contains(passwords.hash("la de antes"));
    }

    @Test
    void givenAShortPassword_whenCreatingIt_thenItIsRefusedBeforeAnyTransaction() {
        User ana = customerWithoutPassword();

        var result = createPassword.execute(new CreatePassword.Command(actor(ana), "corta"));

        assertThat(result).isEqualTo(new CreatePassword.Result.InvalidPassword(PasswordRules.Violation.TOO_SHORT));
        assertThat(changes.transactions()).isZero();
        assertThat(users.findById(ana.id()).orElseThrow().passwordHash()).isEmpty();
    }

    @Test
    void givenAPasswordOverSeventyTwoBytes_whenCreatingIt_thenItIsTooLong() {
        User ana = customerWithoutPassword();

        assertThat(createPassword.execute(new CreatePassword.Command(actor(ana), "ñ".repeat(37))))
            .isEqualTo(new CreatePassword.Result.InvalidPassword(PasswordRules.Violation.TOO_LONG));
    }

    @Test
    void givenADeletedAccount_whenCreatingAPassword_thenItIsNotSignedIn() {
        var gone = Actor.customer(UserId.of(UUID.randomUUID()), Optional.empty(), Optional.empty());

        assertThat(createPassword.execute(new CreatePassword.Command(gone, "una clave larga")))
            .isEqualTo(new CreatePassword.Result.NotSignedIn());
    }

    private User customerWithoutPassword() {
        User ana = User.newExternalCustomer(UserId.of(UUID.randomUUID()), ANA, "Ana", true, NOW);
        users.save(ana);
        return ana;
    }

    private void link(User user, ExternalAccount account) {
        identities.link(ExternalIdentity.link(UUID.randomUUID(), user.id(), account, user.email(), NOW));
    }

    private static Actor actor(User user) {
        return Actor.customer(user.id(), Optional.empty(), Optional.empty());
    }
}
