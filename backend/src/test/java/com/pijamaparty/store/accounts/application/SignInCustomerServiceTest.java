package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.api.EmailVerificationRequested;
import com.pijamaparty.store.accounts.application.SignInCustomer.Command;
import com.pijamaparty.store.accounts.application.SignInCustomer.Result;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** UC-19 (T-E4-02): a customer signs in with email and password once its email is verified. */
class SignInCustomerServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T15:00:00Z");
    private static final String PASSWORD = "ocho1234";

    private final InMemoryUserStore users = new InMemoryUserStore();
    private final InMemoryPasswordHasher passwords = new InMemoryPasswordHasher();
    private final InMemoryAccountEvents events = new InMemoryAccountEvents();
    private User customer;

    @BeforeEach
    void aVerifiedCustomer() {
        customer = User.newCustomer(UserId.of(UUID.randomUUID()), new Email("ana@example.com"), "Ana",
            passwords.hash(PASSWORD), NOW.minus(Duration.ofDays(1)));
        customer.verifyEmail(NOW.minus(Duration.ofDays(1)));
        users.save(customer);
    }

    @Test
    void givenAVerifiedCustomerWithTheRightPassword_whenSigningIn_thenItIsSignedInAndItsFailuresReset() {
        signInAt(NOW, "ana@example.com", "wrong password");

        Result result = signInAt(NOW, " ANA@example.com ", PASSWORD);

        assertThat(result).isEqualTo(new Result.SignedIn(new CustomerView(customer.id(), "ana@example.com", "Ana", Optional.empty())));
        assertThat(users.findById(customer.id()).orElseThrow().failedSignInAttempts()).isZero();
        assertThat(events.published()).isEmpty();
    }

    @Test
    void givenAnUnverifiedCustomerWithTheRightPassword_whenSigningIn_thenItIsRefusedAndANewVerificationEmailRequested() {
        User unverified = User.newCustomer(UserId.of(UUID.randomUUID()), new Email("beto@example.com"), "Beto",
            passwords.hash(PASSWORD), NOW);
        unverified.registerFailedSignIn(NOW);
        users.save(unverified);

        Result result = signInAt(NOW, "beto@example.com", PASSWORD);

        assertThat(result).isEqualTo(new Result.EmailNotVerified());
        assertThat(events.published()).containsExactly(new EmailVerificationRequested(unverified.id().value()));
        assertThat(users.findById(unverified.id()).orElseThrow().failedSignInAttempts()).isOne();
    }

    @Test
    void givenAnUnverifiedCustomerWithAWrongPassword_whenSigningIn_thenItIsAFailureThatSaysNothingAboutVerification() {
        User unverified = User.newCustomer(UserId.of(UUID.randomUUID()), new Email("beto@example.com"), "Beto",
            passwords.hash(PASSWORD), NOW);
        users.save(unverified);

        assertThat(signInAt(NOW, "beto@example.com", "wrong password")).isEqualTo(new Result.InvalidCredentials());
        assertThat(events.published()).isEmpty();
        assertThat(users.findById(unverified.id()).orElseThrow().failedSignInAttempts()).isOne();
    }

    @Test
    void givenAWrongPasswordAnUnknownEmailOrTheAdministrator_whenSigningIn_thenTheResultIsTheSameAndCostsOneHashCheck() {
        users.save(User.newAdministrator(UserId.of(UUID.randomUUID()), new Email("owner@example.com"), "Owner",
            passwords.hash(PASSWORD), NOW));

        Result wrongPassword = signInAt(NOW, "ana@example.com", "wrong password");
        Result unknownEmail = signInAt(NOW, "nobody@example.com", PASSWORD);
        Result notAnEmail = signInAt(NOW, "not an email", PASSWORD);
        Result administrator = signInAt(NOW, "owner@example.com", PASSWORD);

        assertThat(wrongPassword).isEqualTo(new Result.InvalidCredentials());
        assertThat(unknownEmail).isEqualTo(new Result.InvalidCredentials());
        assertThat(notAnEmail).isEqualTo(new Result.InvalidCredentials());
        assertThat(administrator).isEqualTo(new Result.InvalidCredentials());
        assertThat(passwords.checks()).isEqualTo(4);
    }

    @Test
    void givenFourFailures_whenTheFifthFails_thenTheAccountIsLockedUntil15MinutesLater() {
        for (int i = 0; i < 4; i++) {
            assertThat(signInAt(NOW, "ana@example.com", "wrong password")).isEqualTo(new Result.InvalidCredentials());
        }

        assertThat(signInAt(NOW, "ana@example.com", "wrong password"))
            .isEqualTo(new Result.Locked(NOW.plus(Duration.ofMinutes(15))));
    }

    @Test
    void givenALockedAccount_whenSigningInWithTheRightPassword_thenItStaysLockedUntilTheLockEnds() {
        for (int i = 0; i < 5; i++) {
            signInAt(NOW, "ana@example.com", "wrong password");
        }
        int checksBefore = passwords.checks();

        assertThat(signInAt(NOW.plus(Duration.ofMinutes(14)), "ana@example.com", PASSWORD))
            .isEqualTo(new Result.Locked(NOW.plus(Duration.ofMinutes(15))));
        assertThat(passwords.checks()).isEqualTo(checksBefore);
        assertThat(signInAt(NOW.plus(Duration.ofMinutes(15)), "ana@example.com", PASSWORD))
            .isInstanceOf(Result.SignedIn.class);
    }

    private Result signInAt(Instant at, String email, String password) {
        var service = new SignInCustomerService(new InMemoryUserSignInStore(users), passwords, events,
            Clock.fixed(at, ZoneOffset.UTC));
        return service.execute(new Command(email, password));
    }
}
