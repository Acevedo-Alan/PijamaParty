package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.application.ResetPassword.Command;
import com.pijamaparty.store.accounts.application.ResetPassword.Result;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.PasswordRules;
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

/** UC-19 (T-E4-03): the link of the reset email is opened with a new password. */
class ResetPasswordServiceTest {

    private static final Instant SENT_AT = Instant.parse("2026-10-16T15:00:00Z");
    private static final String LINK = InMemoryTokenGenerator.nth(1);
    private static final String NEW_PASSWORD = "nueva-clave";

    private final InMemoryUserStore users = new InMemoryUserStore();
    private final InMemoryUserTokens tokens = new InMemoryUserTokens();
    private final InMemoryPasswordHasher passwords = new InMemoryPasswordHasher();
    private final InMemoryUserSessions sessions = new InMemoryUserSessions();
    private User customer;

    @BeforeEach
    void aLockedUnverifiedCustomerWithALink() {
        customer = User.newCustomer(UserId.of(UUID.randomUUID()), new Email("ana@example.com"), "Ana",
            passwords.hash("ocho1234"), SENT_AT);
        for (int i = 0; i < User.MAX_CONSECUTIVE_FAILURES; i++) {
            customer.registerFailedSignIn(SENT_AT);
        }
        users.save(customer);
        tokens.add(reset(LINK, SENT_AT));
    }

    @Test
    void givenTheLinkWithinItsHalfHour_whenANewPasswordIsSet_thenItReplacesTheOldOneUnlocksVerifiesAndSignsOutEverywhere() {
        Instant openedAt = SENT_AT.plus(Duration.ofMinutes(29));

        assertThat(resetAt(openedAt, LINK, NEW_PASSWORD)).isEqualTo(new Result.Reset());

        User reset = users.findById(customer.id()).orElseThrow();
        assertThat(passwords.matches(NEW_PASSWORD, reset.passwordHash().orElseThrow())).isTrue();
        assertThat(passwords.matches("ocho1234", reset.passwordHash().orElseThrow())).isFalse();
        assertThat(reset.isLockedAt(openedAt)).isFalse();
        assertThat(reset.emailVerified()).isTrue();
        assertThat(tokens.all()).singleElement().satisfies(token -> assertThat(token.usedAt()).contains(openedAt));
        assertThat(sessions.ended()).containsExactly(customer.id());
    }

    @Test
    void givenALinkAlreadyUsed_whenItIsOpenedAgain_thenItIsRefused() {
        resetAt(SENT_AT.plusSeconds(60), LINK, NEW_PASSWORD);

        assertThat(resetAt(SENT_AT.plusSeconds(120), LINK, "otra-clave-2")).isEqualTo(new Result.InvalidLink());
        assertThat(passwords.matches(NEW_PASSWORD, users.findById(customer.id()).orElseThrow().passwordHash()
            .orElseThrow())).isTrue();
        assertThat(sessions.ended()).hasSize(1);
    }

    @Test
    void givenTwoLinksSent_whenTheNewerIsUsed_thenTheOlderStopsWorking() {
        String newer = InMemoryTokenGenerator.nth(2);
        tokens.add(reset(newer, SENT_AT.plusSeconds(60)));
        resetAt(SENT_AT.plusSeconds(120), newer, NEW_PASSWORD);

        assertThat(resetAt(SENT_AT.plusSeconds(180), LINK, "otra-clave-2")).isEqualTo(new Result.InvalidLink());
    }

    @Test
    void givenAnExpiredLink_whenItIsOpened_thenItIsRefusedAndNothingChanges() {
        assertThat(resetAt(SENT_AT.plus(Duration.ofMinutes(30)), LINK, NEW_PASSWORD))
            .isEqualTo(new Result.InvalidLink());

        User unchanged = users.findById(customer.id()).orElseThrow();
        assertThat(passwords.matches("ocho1234", unchanged.passwordHash().orElseThrow())).isTrue();
        assertThat(unchanged.emailVerified()).isFalse();
        assertThat(sessions.ended()).isEmpty();
    }

    @Test
    void givenAVerificationLink_whenItIsOpenedAsAReset_thenItIsRefused() {
        String verification = InMemoryTokenGenerator.nth(2);
        tokens.add(UserToken.issue(UUID.randomUUID(), customer.id(), UserTokenPurpose.EMAIL_VERIFICATION,
            new AccountToken(verification).hash(), SENT_AT));

        assertThat(resetAt(SENT_AT.plusSeconds(60), verification, NEW_PASSWORD)).isEqualTo(new Result.InvalidLink());
    }

    @Test
    void givenAPasswordThatBreaksTheRules_whenSet_thenItIsRefusedAndTheLinkStaysUnused() {
        assertThat(resetAt(SENT_AT, LINK, "siete77"))
            .isEqualTo(new Result.InvalidPassword(PasswordRules.Violation.TOO_SHORT));
        assertThat(resetAt(SENT_AT, LINK, "ñ".repeat(37)))
            .isEqualTo(new Result.InvalidPassword(PasswordRules.Violation.TOO_LONG));

        assertThat(tokens.lookups()).isZero();
        assertThat(resetAt(SENT_AT.plusSeconds(60), LINK, NEW_PASSWORD)).isEqualTo(new Result.Reset());
    }

    @Test
    void givenAnUnknownOrMalformedToken_whenOpened_thenItIsRefusedAndOnlyAWellFormedOneIsLookedUp() {
        assertThat(resetAt(SENT_AT, InMemoryTokenGenerator.nth(9), NEW_PASSWORD)).isEqualTo(new Result.InvalidLink());
        assertThat(resetAt(SENT_AT, "short", NEW_PASSWORD)).isEqualTo(new Result.InvalidLink());
        assertThat(resetAt(SENT_AT, null, NEW_PASSWORD)).isEqualTo(new Result.InvalidLink());

        assertThat(tokens.lookups()).isOne();
    }

    private UserToken reset(String token, Instant at) {
        return UserToken.issue(UUID.randomUUID(), customer.id(), UserTokenPurpose.PASSWORD_RESET,
            new AccountToken(token).hash(), at);
    }

    private Result resetAt(Instant at, String token, String password) {
        var service = new ResetPasswordService(tokens, tokens, users, users, passwords, sessions,
            Clock.fixed(at, ZoneOffset.UTC));
        return service.execute(new Command(token, password));
    }
}
