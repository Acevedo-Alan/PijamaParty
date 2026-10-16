package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.application.VerifyCustomerEmail.Command;
import com.pijamaparty.store.accounts.application.VerifyCustomerEmail.Result;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserToken;
import com.pijamaparty.store.accounts.domain.UserTokenPurpose;
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

/** UC-19 (T-E4-02): the link of the verification email is opened. */
class VerifyCustomerEmailServiceTest {

    private static final Instant SENT_AT = Instant.parse("2026-10-16T15:00:00Z");
    private static final String LINK = InMemoryTokenGenerator.nth(1);

    private final InMemoryUserStore users = new InMemoryUserStore();
    private final InMemoryUserTokens tokens = new InMemoryUserTokens();
    private User customer;

    @BeforeEach
    void anUnverifiedCustomerWithALink() {
        customer = User.newCustomer(UserId.of(UUID.randomUUID()), new Email("ana@example.com"), "Ana",
            new InMemoryPasswordHasher().hash("ocho1234"), SENT_AT);
        users.save(customer);
        tokens.add(UserToken.issue(UUID.randomUUID(), customer.id(), UserTokenPurpose.EMAIL_VERIFICATION,
            new AccountToken(LINK).hash(), SENT_AT));
    }

    @Test
    void givenTheLinkWithinItsDay_whenItIsOpened_thenTheEmailIsVerifiedAndTheTokenUsed() {
        Instant openedAt = SENT_AT.plus(Duration.ofHours(23));

        assertThat(verifyAt(openedAt, LINK)).isEqualTo(new Result.Verified());

        User verified = users.findById(customer.id()).orElseThrow();
        assertThat(verified.emailVerified()).isTrue();
        assertThat(verified.updatedAt()).isEqualTo(openedAt);
        assertThat(tokens.all()).singleElement().satisfies(token -> assertThat(token.usedAt()).contains(openedAt));
    }

    @Test
    void givenALinkAlreadyUsed_whenItIsOpenedAgain_thenItIsRefused() {
        verifyAt(SENT_AT.plusSeconds(60), LINK);

        assertThat(verifyAt(SENT_AT.plusSeconds(120), LINK)).isEqualTo(new Result.InvalidLink());
    }

    @Test
    void givenAnExpiredLink_whenItIsOpened_thenItIsRefusedAndTheEmailStaysUnverified() {
        assertThat(verifyAt(SENT_AT.plus(Duration.ofHours(24)), LINK)).isEqualTo(new Result.InvalidLink());

        assertThat(users.findById(customer.id()).orElseThrow().emailVerified()).isFalse();
        assertThat(tokens.all()).singleElement().satisfies(token -> assertThat(token.usedAt()).isEmpty());
    }

    @Test
    void givenAPasswordResetLink_whenItIsOpenedAsAVerification_thenItIsRefused() {
        String reset = InMemoryTokenGenerator.nth(2);
        tokens.add(UserToken.issue(UUID.randomUUID(), customer.id(), UserTokenPurpose.PASSWORD_RESET,
            new AccountToken(reset).hash(), SENT_AT));

        assertThat(verifyAt(SENT_AT.plusSeconds(60), reset)).isEqualTo(new Result.InvalidLink());
        assertThat(users.findById(customer.id()).orElseThrow().emailVerified()).isFalse();
    }

    @Test
    void givenAnUnknownOrMalformedToken_whenItIsOpened_thenItIsRefusedAndOnlyAWellFormedOneIsLookedUp() {
        assertThat(verifyAt(SENT_AT, InMemoryTokenGenerator.nth(9))).isEqualTo(new Result.InvalidLink());
        assertThat(verifyAt(SENT_AT, "short")).isEqualTo(new Result.InvalidLink());
        assertThat(verifyAt(SENT_AT, LINK + "!")).isEqualTo(new Result.InvalidLink());
        assertThat(verifyAt(SENT_AT, null)).isEqualTo(new Result.InvalidLink());

        assertThat(tokens.lookups()).isOne();
    }

    @Test
    void givenTwoLinksSent_whenTheSecondIsOpenedAfterTheFirst_thenItIsUsedAndChangesNothingElse() {
        String second = InMemoryTokenGenerator.nth(2);
        tokens.add(UserToken.issue(UUID.randomUUID(), customer.id(), UserTokenPurpose.EMAIL_VERIFICATION,
            new AccountToken(second).hash(), SENT_AT.plusSeconds(30)));
        Instant firstOpenedAt = SENT_AT.plusSeconds(60);
        verifyAt(firstOpenedAt, LINK);

        assertThat(verifyAt(SENT_AT.plusSeconds(90), second)).isEqualTo(new Result.Verified());
        assertThat(users.findById(customer.id()).orElseThrow().updatedAt()).isEqualTo(firstOpenedAt);
    }

    private Result verifyAt(Instant at, String token) {
        var service = new VerifyCustomerEmailService(tokens, users, users, Clock.fixed(at, ZoneOffset.UTC));
        return service.execute(new Command(token));
    }
}
