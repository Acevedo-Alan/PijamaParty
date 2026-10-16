package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.api.EmailVerificationLinks.EmailVerificationLink;
import com.pijamaparty.store.accounts.domain.Email;
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

/** UC-19 (T-E4-02): the verification link is minted when its email is sent. */
class IssueEmailVerificationLinkServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T15:00:00Z");

    private final InMemoryUserStore users = new InMemoryUserStore();
    private final InMemoryUserTokens tokens = new InMemoryUserTokens();
    private final IssueEmailVerificationLinkService service = new IssueEmailVerificationLinkService(users, tokens,
        new InMemoryTokenGenerator(), new InMemoryIdGenerator(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void givenAnUnverifiedCustomer_whenItsEmailIsSent_thenALinkOf24HoursIsMintedAndOnlyItsHashKept() {
        User customer = customer();

        EmailVerificationLink link = service.issue(customer.id().value()).orElseThrow();

        String token = InMemoryTokenGenerator.nth(1);
        assertThat(link).isEqualTo(new EmailVerificationLink("ana@example.com", "Ana Pérez", new AccountToken(token)));
        assertThat(tokens.all()).singleElement().satisfies(stored -> {
            assertThat(stored.userId()).isEqualTo(customer.id());
            assertThat(stored.purpose()).isEqualTo(UserTokenPurpose.EMAIL_VERIFICATION);
            assertThat(stored.tokenHash()).isEqualTo(new AccountToken(token).hash()).isNotEqualTo(token);
            assertThat(stored.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        });
    }

    @Test
    void givenTwoEmailsSent_whenEachMintsALink_thenBothLinksAreDifferentAndKept() {
        User customer = customer();

        AccountToken first = service.issue(customer.id().value()).orElseThrow().token();
        AccountToken second = service.issue(customer.id().value()).orElseThrow().token();

        assertThat(first).isNotEqualTo(second);
        assertThat(tokens.all()).hasSize(2);
    }

    @Test
    void givenAVerifiedCustomerAnUnknownUserOrTheAdministrator_whenTheEmailIsSent_thenThereIsNoLink() {
        User verified = customer();
        verified.verifyEmail(NOW);
        users.save(verified);
        User administrator = User.newAdministrator(UserId.of(UUID.randomUUID()), new Email("owner@example.com"),
            "Owner", new InMemoryPasswordHasher().hash("correct horse battery"), NOW);
        users.save(administrator);

        assertThat(service.issue(verified.id().value())).isEmpty();
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
