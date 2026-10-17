package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.accounts.api.EmailVerificationRequested;
import com.pijamaparty.store.accounts.api.LinkConfirmationRequested;
import com.pijamaparty.store.accounts.application.SignInWithExternalAccount.Command;
import com.pijamaparty.store.accounts.application.SignInWithExternalAccount.Result;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.accounts.domain.PendingLink;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.application.InMemoryIdGenerator;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-27 (T-E4-14): a visitor comes back from Google or Facebook. */
class SignInWithExternalAccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T15:00:00Z");
    private static final ExternalAccount GOOGLE = new ExternalAccount(ExternalProvider.GOOGLE, "109876543210987654321");
    private static final ExternalAccount FACEBOOK = new ExternalAccount(ExternalProvider.FACEBOOK, "10229876543210");
    private static final Email ANA = new Email("ana@example.com");

    private final InMemoryExternalSignIns signIns = new InMemoryExternalSignIns();
    private final InMemoryExternalIdentities identities = new InMemoryExternalIdentities();
    private final InMemoryUserStore users = new InMemoryUserStore();
    private final InMemoryPendingLinks pendingLinks = new InMemoryPendingLinks();
    private final InMemoryAccountEvents events = new InMemoryAccountEvents();
    private final SignInWithExternalAccountService service = new SignInWithExternalAccountService(signIns,
        identities, identities, users, users, users, users, pendingLinks, pendingLinks, events,
        new InMemoryIdGenerator(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void givenNoAccount_whenContinuingWithGoogle_thenAVerifiedCustomerWithoutAPasswordIsCreatedLinkedAndSignedIn() {
        Result result = service.execute(google("ana@example.com", "  Ana Pérez  "));

        UserId created = UserId.of(InMemoryIdGenerator.nth(1));
        assertThat(result).isEqualTo(new Result.SignedIn(created));
        User customer = users.findById(created).orElseThrow();
        assertThat(customer.email()).isEqualTo(ANA);
        assertThat(customer.name()).isEqualTo("Ana Pérez");
        assertThat(customer.emailVerified()).isTrue();
        assertThat(customer.passwordHash()).isEmpty();
        assertThat(identities.find(GOOGLE)).get().satisfies(identity -> {
            assertThat(identity.userId()).isEqualTo(created);
            assertThat(identity.email()).isEqualTo(ANA);
        });
        assertThat(events.published()).isEmpty();
        assertThat(signIns.transactions()).isOne();
    }

    @Test
    void givenNoAccount_whenContinuingWithFacebook_thenAnUnverifiedCustomerIsCreatedSignedInAndAskedToVerify() {
        Result result = service.execute(facebook(" Ana@Example.com ", "Ana"));

        UserId created = UserId.of(InMemoryIdGenerator.nth(1));
        assertThat(result).isEqualTo(new Result.SignedIn(created));
        User customer = users.findById(created).orElseThrow();
        assertThat(customer.email()).isEqualTo(ANA);
        assertThat(customer.emailVerified()).isFalse();
        assertThat(customer.passwordHash()).isEmpty();
        assertThat(identities.find(FACEBOOK)).get().extracting(ExternalIdentity::userId).isEqualTo(created);
        assertThat(events.published()).containsExactly(new EmailVerificationRequested(created.value()));
    }

    @Test
    void givenAGoogleEmailItDoesNotVouchFor_whenContinuing_thenTheNewCustomerIsUnverified() {
        Result result = service.execute(new Command(GOOGLE, Optional.of("ana@example.com"), false,
            Optional.of("Ana")));

        assertThat(result).isInstanceOf(Result.SignedIn.class);
        assertThat(users.findByEmail(ANA).orElseThrow().emailVerified()).isFalse();
    }

    @Test
    void givenAnExternalAccountAlreadyLinked_whenContinuing_thenItsCustomerSignsInWithNothingNew() {
        User ana = verifiedCustomerWithPassword();
        identities.link(ExternalIdentity.link(UUID.randomUUID(), ana.id(), FACEBOOK, ANA, NOW));

        Result result = service.execute(facebook("otro@example.com", "Otro nombre"));

        assertThat(result).isEqualTo(new Result.SignedIn(ana.id()));
        assertThat(users.findByEmail(new Email("otro@example.com"))).isEmpty();
        assertThat(identities.all()).hasSize(1);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void givenACustomerWithAPassword_whenContinuingWithGoogleWithTheSameVerifiedEmail_thenItIsLinkedAndSignsIn() {
        User ana = verifiedCustomerWithPassword();

        Result result = service.execute(google("ANA@example.com", "Ana de Google"));

        assertThat(result).isEqualTo(new Result.SignedIn(ana.id()));
        assertThat(identities.find(GOOGLE)).get().extracting(ExternalIdentity::userId).isEqualTo(ana.id());
        User after = users.findById(ana.id()).orElseThrow();
        assertThat(after.passwordHash()).isPresent();
        assertThat(after.name()).isEqualTo("Ana");
        assertThat(users.findByEmail(ANA)).get().extracting(User::id).isEqualTo(ana.id());
    }

    @Test
    void givenAnUnverifiedCustomerWithAPassword_whenContinuingWithGoogle_thenItIsVerifiedAndLosesItsPassword() {
        User ana = User.newCustomer(UserId.of(UUID.randomUUID()), ANA, "Ana",
            new InMemoryPasswordHasher().hash("ocho1234"), NOW.minusSeconds(3600));
        users.save(ana);

        Result result = service.execute(google("ana@example.com", "Ana"));

        assertThat(result).isEqualTo(new Result.SignedIn(ana.id()));
        User after = users.findById(ana.id()).orElseThrow();
        assertThat(after.emailVerified()).isTrue();
        assertThat(after.passwordHash()).isEmpty();
        assertThat(after.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void givenACustomerWithAnAccount_whenContinuingWithFacebookWithTheSameEmail_thenItDoesNotSignInUntilConfirmed() {
        User ana = verifiedCustomerWithPassword();

        Result result = service.execute(facebook("ana@example.com", "Ana"));

        assertThat(result).isEqualTo(new Result.LinkConfirmationSent());
        assertThat(identities.all()).isEmpty();
        assertThat(pendingLinks.find(ana.id())).contains(new PendingLink(ana.id(), FACEBOOK, NOW));
        assertThat(events.published()).containsExactly(new LinkConfirmationRequested(ana.id().value()));
        assertThat(users.findById(ana.id()).orElseThrow().passwordHash()).isPresent();
    }

    @Test
    void givenAFreshPendingLinkOfAnotherFacebookAccount_whenContinuingWithFacebook_thenTheFirstRequestKeepsWaiting() {
        User ana = verifiedCustomerWithPassword();
        var first = new PendingLink(ana.id(), new ExternalAccount(ExternalProvider.FACEBOOK, "999"),
            NOW.minus(Duration.ofHours(23)));
        pendingLinks.request(first);

        Result result = service.execute(facebook("ana@example.com", "Ana"));

        assertThat(result).isEqualTo(new Result.LinkConfirmationSent());
        assertThat(pendingLinks.find(ana.id())).contains(first);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void givenAPendingLinkOfAnotherFacebookAccount24HoursOld_whenContinuingWithFacebook_thenTheNewOneReplacesIt() {
        User ana = verifiedCustomerWithPassword();
        pendingLinks.request(new PendingLink(ana.id(), new ExternalAccount(ExternalProvider.FACEBOOK, "999"),
            NOW.minus(Duration.ofHours(24))));

        service.execute(facebook("ana@example.com", "Ana"));

        assertThat(pendingLinks.find(ana.id())).contains(new PendingLink(ana.id(), FACEBOOK, NOW));
        assertThat(events.published()).containsExactly(new LinkConfirmationRequested(ana.id().value()));
    }

    @Test
    void givenACustomerWithAnotherAccountOfTheProvider_whenContinuingWithANewOne_thenItIsRefusedAsAlreadyLinked() {
        User ana = verifiedCustomerWithPassword();
        var otherGoogle = new ExternalAccount(ExternalProvider.GOOGLE, "200000000000000000001");
        identities.link(ExternalIdentity.link(UUID.randomUUID(), ana.id(), otherGoogle, ANA, NOW));

        Result result = service.execute(google("ana@example.com", "Ana"));

        assertThat(result).isEqualTo(new Result.ProviderAlreadyLinked());
        assertThat(identities.find(GOOGLE)).isEmpty();
    }

    @Test
    void givenTheAdministratorsEmail_whenContinuingWithGoogle_thenNothingIsCreatedOrLinked() {
        User administrator = User.newAdministrator(UserId.of(UUID.randomUUID()), new Email("owner@example.com"),
            "Owner", new InMemoryPasswordHasher().hash("doce12345678"), NOW);
        users.save(administrator);

        Result result = service.execute(google("owner@example.com", "Owner"));

        assertThat(result).isEqualTo(new Result.Refused());
        assertThat(identities.all()).isEmpty();
        assertThat(pendingLinks.find(administrator.id())).isEmpty();
    }

    @Test
    void givenTheAdministratorsEmail_whenContinuingWithFacebook_thenNoLinkConfirmationIsSent() {
        User administrator = User.newAdministrator(UserId.of(UUID.randomUUID()), new Email("owner@example.com"),
            "Owner", new InMemoryPasswordHasher().hash("doce12345678"), NOW);
        users.save(administrator);

        assertThat(service.execute(facebook("owner@example.com", "Owner"))).isEqualTo(new Result.Refused());

        assertThat(events.published()).isEmpty();
    }

    @Test
    void givenNoEmail_whenContinuingWithFacebook_thenAnEmailIsRequiredAndNothingIsCreated() {
        Result result = service.execute(new Command(FACEBOOK, Optional.empty(), false, Optional.of("Ana")));

        assertThat(result).isEqualTo(new Result.EmailRequired());
        assertThat(users.findById(UserId.of(InMemoryIdGenerator.nth(1)))).isEmpty();
        assertThat(identities.all()).isEmpty();
    }

    @Test
    void givenAnEmailThatCannotBeOne_whenContinuing_thenItIsRefused() {
        assertThat(service.execute(google("not an email", "Ana"))).isEqualTo(new Result.Refused());

        assertThat(identities.all()).isEmpty();
    }

    @Test
    void givenNoName_whenANewCustomerIsCreated_thenItIsNamedAfterItsEmail() {
        service.execute(new Command(GOOGLE, Optional.of("ana.perez@example.com"), true, Optional.empty()));

        assertThat(users.findByEmail(new Email("ana.perez@example.com")).orElseThrow().name()).isEqualTo("ana.perez");
    }

    @Test
    void givenAnotherSignInLinkedTheNewExternalAccountFirst_whenCreatingTheCustomer_thenItFailsSoTheAccountRollsBack() {
        LinkExternalIdentities alreadyTaken = identity -> false;
        var racing = new SignInWithExternalAccountService(signIns, identities, alreadyTaken, users, users, users,
            users, pendingLinks, pendingLinks, events, new InMemoryIdGenerator(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> racing.execute(google("ana@example.com", "Ana")))
            .isInstanceOf(ConcurrentExternalSignInException.class);
    }

    private User verifiedCustomerWithPassword() {
        User ana = User.newCustomer(UserId.of(UUID.randomUUID()), ANA, "Ana",
            new InMemoryPasswordHasher().hash("ocho1234"), NOW.minusSeconds(3600));
        ana.verifyEmail(NOW.minusSeconds(1800));
        users.save(ana);
        return ana;
    }

    private static Command google(String email, String name) {
        return new Command(GOOGLE, Optional.of(email), true, Optional.of(name));
    }

    /** Facebook never says whether the email is verified, whatever the adapter passes. */
    private static Command facebook(String email, String name) {
        return new Command(FACEBOOK, Optional.of(email), true, Optional.of(name));
    }
}
