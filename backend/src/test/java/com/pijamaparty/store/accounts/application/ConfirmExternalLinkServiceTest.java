package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.application.ConfirmExternalLink.Command;
import com.pijamaparty.store.accounts.application.ConfirmExternalLink.Result;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.accounts.domain.PendingLink;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserToken;
import com.pijamaparty.store.accounts.domain.UserTokenPurpose;
import com.pijamaparty.store.platform.application.InMemoryIdGenerator;
import com.pijamaparty.store.platform.application.InMemoryTokenGenerator;
import com.pijamaparty.store.platform.domain.AccessToken.AccountToken;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** UC-27 (T-E4-14): the link of the link confirmation email is opened. */
class ConfirmExternalLinkServiceTest {

    private static final Instant SENT_AT = Instant.parse("2026-10-17T15:00:00Z");
    private static final String LINK = InMemoryTokenGenerator.nth(1);
    private static final ExternalAccount FACEBOOK = new ExternalAccount(ExternalProvider.FACEBOOK, "10229876543210");

    private final InMemoryUserStore users = new InMemoryUserStore();
    private final InMemoryUserTokens tokens = new InMemoryUserTokens();
    private final InMemoryExternalIdentities identities = new InMemoryExternalIdentities();
    private final InMemoryPendingLinks pendingLinks = new InMemoryPendingLinks();
    private User customer;

    @BeforeEach
    void anUnverifiedCustomerWithAPasswordAndALink() {
        customer = User.newCustomer(UserId.of(UUID.randomUUID()), new Email("ana@example.com"), "Ana",
            new InMemoryPasswordHasher().hash("ocho1234"), SENT_AT.minusSeconds(3600));
        users.save(customer);
        pendingLinks.request(new PendingLink(customer.id(), FACEBOOK, SENT_AT));
        tokens.add(UserToken.issueLinkConfirmation(UUID.randomUUID(), customer.id(), FACEBOOK,
            new AccountToken(LINK).hash(), SENT_AT));
    }

    @Test
    void givenTheLinkWithinItsDay_whenItIsOpened_thenTheAccountIsLinkedVerifiedAndLosesItsPassword() {
        Instant openedAt = SENT_AT.plus(Duration.ofHours(23));

        assertThat(confirmAt(openedAt, LINK)).isEqualTo(new Result.Linked());

        assertThat(identities.find(FACEBOOK)).get().satisfies(identity -> {
            assertThat(identity.userId()).isEqualTo(customer.id());
            assertThat(identity.email()).isEqualTo(customer.email());
            assertThat(identity.createdAt()).isEqualTo(openedAt);
        });
        User linked = users.findById(customer.id()).orElseThrow();
        assertThat(linked.emailVerified()).isTrue();
        assertThat(linked.passwordHash()).isEmpty();
        assertThat(tokens.all()).singleElement().satisfies(token -> assertThat(token.usedAt()).contains(openedAt));
        assertThat(pendingLinks.find(customer.id())).isEmpty();
    }

    @Test
    void givenAVerifiedCustomer_whenItConfirms_thenItKeepsItsPassword() {
        customer.verifyEmail(SENT_AT);
        users.save(customer);

        assertThat(confirmAt(SENT_AT.plusSeconds(60), LINK)).isEqualTo(new Result.Linked());

        assertThat(users.findById(customer.id()).orElseThrow().passwordHash()).isPresent();
    }

    @Test
    void givenALinkAlreadyUsed_whenItIsOpenedAgain_thenItIsRefused() {
        confirmAt(SENT_AT.plusSeconds(60), LINK);

        assertThat(confirmAt(SENT_AT.plusSeconds(120), LINK)).isEqualTo(new Result.InvalidLink());
    }

    @Test
    void givenAnExpiredLink_whenItIsOpened_thenNothingIsLinked() {
        assertThat(confirmAt(SENT_AT.plus(Duration.ofHours(24)), LINK)).isEqualTo(new Result.InvalidLink());

        assertThat(identities.all()).isEmpty();
        assertThat(users.findById(customer.id()).orElseThrow().emailVerified()).isFalse();
    }

    @Test
    void givenAVerificationLink_whenItIsOpenedAsALinkConfirmation_thenItIsRefused() {
        String verification = InMemoryTokenGenerator.nth(2);
        tokens.add(UserToken.issue(UUID.randomUUID(), customer.id(), UserTokenPurpose.EMAIL_VERIFICATION,
            new AccountToken(verification).hash(), SENT_AT));

        assertThat(confirmAt(SENT_AT.plusSeconds(60), verification)).isEqualTo(new Result.InvalidLink());
        assertThat(identities.all()).isEmpty();
    }

    @Test
    void givenAMalformedToken_whenItIsOpened_thenItIsRefusedWithoutALookup() {
        assertThat(confirmAt(SENT_AT, "short")).isEqualTo(new Result.InvalidLink());
        assertThat(confirmAt(SENT_AT, null)).isEqualTo(new Result.InvalidLink());

        assertThat(tokens.lookups()).isZero();
    }

    @Test
    void givenTheExternalAccountLinkedToSomeoneElse_whenTheLinkIsOpened_thenItIsRefusedAndTheAccountUnchanged() {
        identities.link(ExternalIdentity.link(UUID.randomUUID(), UserId.of(UUID.randomUUID()), FACEBOOK,
            new Email("otra@example.com"), SENT_AT));

        assertThat(confirmAt(SENT_AT.plusSeconds(60), LINK)).isEqualTo(new Result.AlreadyLinked());

        assertThat(users.findById(customer.id()).orElseThrow().emailVerified()).isFalse();
        assertThat(users.findById(customer.id()).orElseThrow().passwordHash()).isPresent();
    }

    @Test
    void givenTheCustomerAlreadyHasAnotherFacebookAccount_whenTheLinkIsOpened_thenItIsRefused() {
        identities.link(ExternalIdentity.link(UUID.randomUUID(), customer.id(),
            new ExternalAccount(ExternalProvider.FACEBOOK, "999"), customer.email(), SENT_AT));

        assertThat(confirmAt(SENT_AT.plusSeconds(60), LINK)).isEqualTo(new Result.AlreadyLinked());

        assertThat(identities.find(FACEBOOK)).isEmpty();
    }

    @Test
    void givenTwoLinksOfTheSameAccount_whenTheSecondIsOpenedAfterTheFirst_thenItIsLinkedAlready() {
        String second = InMemoryTokenGenerator.nth(2);
        tokens.add(UserToken.issueLinkConfirmation(UUID.randomUUID(), customer.id(), FACEBOOK,
            new AccountToken(second).hash(), SENT_AT.plusSeconds(30)));
        confirmAt(SENT_AT.plusSeconds(60), LINK);

        assertThat(confirmAt(SENT_AT.plusSeconds(90), second)).isEqualTo(new Result.Linked());
        assertThat(identities.all()).hasSize(1);
    }

    private Result confirmAt(Instant at, String token) {
        var service = new ConfirmExternalLinkService(tokens, users, users, identities, identities, pendingLinks,
            new InMemoryIdGenerator(), Clock.fixed(at, ZoneOffset.UTC));
        return service.execute(new Command(token));
    }
}
