package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.api.LinkConfirmationLinks.LinkConfirmationLink;
import com.pijamaparty.store.accounts.api.LinkConfirmationLinks.Provider;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.accounts.domain.PendingLink;
import com.pijamaparty.store.accounts.domain.User;
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
import org.junit.jupiter.api.Test;

/** UC-27 (T-E4-14): the link confirmation link is minted when its email is sent, from the pending link. */
class IssueLinkConfirmationLinkServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T15:00:00Z");
    private static final ExternalAccount FACEBOOK = new ExternalAccount(ExternalProvider.FACEBOOK, "10229876543210");

    private final InMemoryUserStore users = new InMemoryUserStore();
    private final InMemoryPendingLinks pendingLinks = new InMemoryPendingLinks();
    private final InMemoryUserTokens tokens = new InMemoryUserTokens();
    private final IssueLinkConfirmationLinkService service = new IssueLinkConfirmationLinkService(users,
        pendingLinks, tokens, new InMemoryTokenGenerator(), new InMemoryIdGenerator(),
        Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void givenAPendingLink_whenItsEmailIsSent_thenALinkOf24HoursNamingTheExternalAccountIsMintedAndOnlyItsHashKept() {
        User customer = customer();
        pendingLinks.request(new PendingLink(customer.id(), FACEBOOK, NOW.minusSeconds(60)));

        LinkConfirmationLink link = service.issue(customer.id().value()).orElseThrow();

        String token = InMemoryTokenGenerator.nth(1);
        assertThat(link).isEqualTo(new LinkConfirmationLink("ana@example.com", "Ana Pérez", Provider.FACEBOOK,
            new AccountToken(token)));
        assertThat(tokens.all()).singleElement().satisfies(stored -> {
            assertThat(stored.userId()).isEqualTo(customer.id());
            assertThat(stored.purpose()).isEqualTo(UserTokenPurpose.LINK_CONFIRMATION);
            assertThat(stored.externalAccount()).contains(FACEBOOK);
            assertThat(stored.tokenHash()).isEqualTo(new AccountToken(token).hash()).isNotEqualTo(token);
            assertThat(stored.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        });
    }

    @Test
    void givenTheLatestRequestReplacedTheFirst_whenTheEmailIsSent_thenTheLinkNamesTheLatestExternalAccount() {
        User customer = customer();
        pendingLinks.request(new PendingLink(customer.id(), FACEBOOK, NOW.minusSeconds(60)));
        var latest = new ExternalAccount(ExternalProvider.FACEBOOK, "999");
        pendingLinks.request(new PendingLink(customer.id(), latest, NOW));

        service.issue(customer.id().value());

        assertThat(tokens.all()).singleElement().satisfies(stored ->
            assertThat(stored.externalAccount()).contains(latest));
    }

    @Test
    void givenNoPendingLinkAnUnknownUserOrTheAdministrator_whenTheEmailIsSent_thenThereIsNoLink() {
        User withoutPendingLink = customer();
        User administrator = User.newAdministrator(UserId.of(UUID.randomUUID()), new Email("owner@example.com"),
            "Owner", new InMemoryPasswordHasher().hash("correct horse battery"), NOW);
        users.save(administrator);
        pendingLinks.request(new PendingLink(administrator.id(), FACEBOOK, NOW));

        assertThat(service.issue(withoutPendingLink.id().value())).isEmpty();
        assertThat(service.issue(UUID.randomUUID())).isEmpty();
        assertThat(service.issue(administrator.id().value())).isEmpty();
        assertThat(tokens.all()).isEmpty();
    }

    private User customer() {
        User customer = User.newCustomer(UserId.of(UUID.randomUUID()), new Email("ana@example.com"), "Ana Pérez",
            new InMemoryPasswordHasher().hash("ocho1234"), NOW);
        users.save(customer);
        return customer;
    }
}
