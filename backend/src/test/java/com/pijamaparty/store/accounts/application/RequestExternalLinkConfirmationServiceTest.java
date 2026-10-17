package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.api.LinkAttemptNoticeRequested;
import com.pijamaparty.store.accounts.api.LinkConfirmationRequested;
import com.pijamaparty.store.accounts.application.RequestExternalLinkConfirmation.Command;
import com.pijamaparty.store.accounts.application.RequestExternalLinkConfirmation.Result;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.accounts.domain.PendingLink;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.application.InMemoryIdGenerator;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-27 (T-E4-14): a Facebook account came without an email, and the visitor gives one. */
class RequestExternalLinkConfirmationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T15:00:00Z");
    private static final ExternalAccount FACEBOOK = new ExternalAccount(ExternalProvider.FACEBOOK, "10229876543210");

    private final InMemoryExternalSignIns signIns = new InMemoryExternalSignIns();
    private final InMemoryExternalIdentities identities = new InMemoryExternalIdentities();
    private final InMemoryUserStore users = new InMemoryUserStore();
    private final InMemoryPendingLinks pendingLinks = new InMemoryPendingLinks();
    private final InMemoryAccountEvents events = new InMemoryAccountEvents();
    private final RequestExternalLinkConfirmationService service = new RequestExternalLinkConfirmationService(
        signIns, identities, users, users, pendingLinks, pendingLinks, events, new InMemoryIdGenerator(),
        Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void givenNoAccountWithTheEmail_whenItIsGiven_thenAnAccountNobodySignsInToWaitsForTheLinkConfirmation() {
        assertThat(service.execute(new Command(FACEBOOK, Optional.of("Ana Pérez"), " Ana@Example.com ")))
            .isEqualTo(new Result.Requested());

        User created = users.findByEmail(new Email("ana@example.com")).orElseThrow();
        assertThat(created.id()).isEqualTo(UserId.of(InMemoryIdGenerator.nth(1)));
        assertThat(created.name()).isEqualTo("Ana Pérez");
        assertThat(created.emailVerified()).isFalse();
        assertThat(created.passwordHash()).isEmpty();
        assertThat(identities.all()).isEmpty();
        assertThat(pendingLinks.find(created.id())).contains(new PendingLink(created.id(), FACEBOOK, NOW));
        assertThat(events.published()).containsExactly(new LinkConfirmationRequested(created.id().value()));
    }

    @Test
    void givenACustomerWithTheEmail_whenItIsGiven_thenNothingIsLinkedOrWaitsAndTheCustomerGetsTheNotice() {
        User ana = customer("ana@example.com");

        assertThat(service.execute(new Command(FACEBOOK, Optional.of("Ana"), "ana@example.com")))
            .isEqualTo(new Result.Requested());

        assertThat(pendingLinks.find(ana.id())).isEmpty();
        assertThat(events.published()).containsExactly(new LinkAttemptNoticeRequested(ana.id().value()));
        assertThat(identities.all()).isEmpty();
    }

    @Test
    void givenACustomerWaitingForTheLinkOfAnotherExternalAccount_whenItsEmailIsGiven_thenThatLinkKeepsWaiting() {
        User ana = customer("ana@example.com");
        var own = new PendingLink(ana.id(), new ExternalAccount(ExternalProvider.FACEBOOK, "999"),
            NOW.minusSeconds(60));
        pendingLinks.request(own);

        service.execute(new Command(FACEBOOK, Optional.empty(), "ana@example.com"));

        assertThat(pendingLinks.find(ana.id())).contains(own);
        assertThat(events.published()).containsExactly(new LinkAttemptNoticeRequested(ana.id().value()));
    }

    @Test
    void givenTheVisitorAsksAgainForTheAccountItCreated_whenTheEmailIsGiven_thenTheLinkConfirmationIsSentAgain() {
        service.execute(new Command(FACEBOOK, Optional.of("Ana"), "ana@example.com"));
        UserId created = UserId.of(InMemoryIdGenerator.nth(1));
        var later = new RequestExternalLinkConfirmationService(signIns, identities, users, users, pendingLinks,
            pendingLinks, events, new InMemoryIdGenerator(), Clock.fixed(NOW.plusSeconds(600), ZoneOffset.UTC));

        later.execute(new Command(FACEBOOK, Optional.of("Ana"), "ana@example.com"));

        assertThat(pendingLinks.find(created)).contains(new PendingLink(created, FACEBOOK, NOW.plusSeconds(600)));
        assertThat(events.published()).containsExactly(new LinkConfirmationRequested(created.value()),
            new LinkConfirmationRequested(created.value()));
    }

    @Test
    void givenTheAdministratorsEmail_whenItIsGiven_thenTheAnswerIsTheSameAndNothingIsSent() {
        User administrator = User.newAdministrator(UserId.of(UUID.randomUUID()), new Email("owner@example.com"),
            "Owner", new InMemoryPasswordHasher().hash("doce12345678"), NOW);
        users.save(administrator);

        assertThat(service.execute(new Command(FACEBOOK, Optional.empty(), "owner@example.com")))
            .isEqualTo(new Result.Requested());

        assertThat(events.published()).isEmpty();
        assertThat(pendingLinks.find(administrator.id())).isEmpty();
    }

    @Test
    void givenACustomerThatAlreadyHasAFacebookAccount_whenItsEmailIsGiven_thenTheAnswerIsTheSameAndNothingIsSent() {
        User ana = customer("ana@example.com");
        identities.link(ExternalIdentity.link(UUID.randomUUID(), ana.id(),
            new ExternalAccount(ExternalProvider.FACEBOOK, "999"), ana.email(), NOW));

        assertThat(service.execute(new Command(FACEBOOK, Optional.empty(), "ana@example.com")))
            .isEqualTo(new Result.Requested());

        assertThat(events.published()).isEmpty();
    }

    @Test
    void givenTheExternalAccountWasLinkedMeanwhile_whenAnEmailIsGiven_thenNothingIsCreatedOrSent() {
        identities.link(ExternalIdentity.link(UUID.randomUUID(), UserId.of(UUID.randomUUID()), FACEBOOK,
            new Email("otra@example.com"), NOW));

        assertThat(service.execute(new Command(FACEBOOK, Optional.empty(), "ana@example.com")))
            .isEqualTo(new Result.Requested());

        assertThat(users.findByEmail(new Email("ana@example.com"))).isEmpty();
        assertThat(events.published()).isEmpty();
    }

    @Test
    void givenSomethingThatIsNotAnEmail_whenItIsGiven_thenItIsRefusedWithoutATransaction() {
        assertThat(service.execute(new Command(FACEBOOK, Optional.empty(), "ana"))).isEqualTo(new Result.InvalidEmail());
        assertThat(service.execute(new Command(FACEBOOK, Optional.empty(), null))).isEqualTo(new Result.InvalidEmail());

        assertThat(signIns.transactions()).isZero();
    }

    private User customer(String email) {
        User customer = User.newCustomer(UserId.of(UUID.randomUUID()), new Email(email), "Ana",
            new InMemoryPasswordHasher().hash("ocho1234"), NOW);
        customer.verifyEmail(NOW);
        users.save(customer);
        return customer;
    }
}
