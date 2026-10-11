package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.application.SignInAdministrator.Command;
import com.pijamaparty.store.accounts.application.SignInAdministrator.Result;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.EncryptedSecret;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SignInAdministratorServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T15:00:00Z");
    private static final String PASSWORD = "correct horse battery";

    private final InMemoryUserStore users = new InMemoryUserStore();
    private final InMemoryPasswordHasher passwords = new InMemoryPasswordHasher();
    private User administrator;

    @BeforeEach
    void anAdministrator() {
        administrator = User.newAdministrator(UserId.of(UUID.randomUUID()), Email.parse("owner@example.com").orElseThrow(),
            "Owner", passwords.hash(PASSWORD), NOW.minus(Duration.ofDays(1)));
        users.save(administrator);
    }

    @Test
    void givenCorrectCredentialsWithoutASecondFactor_whenSigningIn_thenEnrollmentComesNext() {
        Result result = signInAt(NOW, " Owner@Example.com ", PASSWORD);

        assertThat(result).isEqualTo(
            new Result.PasswordAccepted(administrator.id(), SignInAdministrator.NextStep.ENROLLMENT));
    }

    @Test
    void givenCorrectCredentialsWithASecondFactor_whenSigningIn_thenTheCodeComesNextAndNothingOpensYet() {
        administrator.enrollSecondFactor(new EncryptedSecret(new byte[] {1}), 1L, NOW.minus(Duration.ofDays(1)));
        users.save(administrator);

        assertThat(signInAt(NOW, "owner@example.com", PASSWORD)).isEqualTo(
            new Result.PasswordAccepted(administrator.id(), SignInAdministrator.NextStep.SECOND_FACTOR));
    }

    @Test
    void givenAWrongPasswordOrAnUnknownEmail_whenSigningIn_thenTheResultIsTheSameAndCostsOneHashCheck() {
        Result wrongPassword = signInAt(NOW, "owner@example.com", "wrong password!");
        int checksAfterWrongPassword = passwords.checks();
        Result unknownEmail = signInAt(NOW, "nobody@example.com", PASSWORD);
        Result notAnEmail = signInAt(NOW, "not an email", PASSWORD);

        assertThat(wrongPassword).isEqualTo(new Result.InvalidCredentials());
        assertThat(unknownEmail).isEqualTo(new Result.InvalidCredentials());
        assertThat(notAnEmail).isEqualTo(new Result.InvalidCredentials());
        assertThat(checksAfterWrongPassword).isEqualTo(1);
        assertThat(passwords.checks()).isEqualTo(3);
    }

    @Test
    void givenACustomerWithTheRightPassword_whenSigningInToTheBackOffice_thenTheCredentialsAreInvalid() {
        var customer = User.restore(UserId.of(UUID.randomUUID()), Email.parse("ana@example.com").orElseThrow(), true,
            passwords.hash(PASSWORD), "Ana", UserRole.CUSTOMER, 0, null, NOW, NOW);
        users.save(customer);

        assertThat(signInAt(NOW, "ana@example.com", PASSWORD)).isEqualTo(new Result.InvalidCredentials());
    }

    @Test
    void givenFourFailures_whenTheFifthFails_thenTheAccountIsLockedUntil15MinutesLater() {
        for (int i = 0; i < 4; i++) {
            assertThat(signInAt(NOW, "owner@example.com", "wrong password!")).isEqualTo(new Result.InvalidCredentials());
        }

        assertThat(signInAt(NOW, "owner@example.com", "wrong password!"))
            .isEqualTo(new Result.Locked(NOW.plus(Duration.ofMinutes(15))));
    }

    @Test
    void givenALockedAccount_whenSigningInWithTheRightPassword_thenItStaysLockedWithoutCheckingThePassword() {
        lock();
        int checksBefore = passwords.checks();

        Result result = signInAt(NOW.plus(Duration.ofMinutes(10)), "owner@example.com", PASSWORD);

        assertThat(result).isEqualTo(new Result.Locked(NOW.plus(Duration.ofMinutes(15))));
        assertThat(passwords.checks()).isEqualTo(checksBefore);
    }

    @Test
    void givenAnExpiredLock_whenSigningInWithTheRightPassword_thenThePasswordIsAccepted() {
        lock();

        assertThat(signInAt(NOW.plus(Duration.ofMinutes(15)), "owner@example.com", PASSWORD))
            .isInstanceOf(Result.PasswordAccepted.class);
        assertThat(users.findById(administrator.id()).orElseThrow().isLockedAt(NOW.plus(Duration.ofMinutes(15))))
            .as("the expired lock is only cleared when the whole sign-in succeeds").isFalse();
    }

    @Test
    void givenFailuresThenARightPassword_whenFailingAgain_thenTheCounterGoesOnUntilTheWholeSignInSucceeds() {
        for (int i = 0; i < 4; i++) {
            signInAt(NOW, "owner@example.com", "wrong password!");
        }
        signInAt(NOW, "owner@example.com", PASSWORD);

        assertThat(users.findById(administrator.id()).orElseThrow().failedSignInAttempts()).isEqualTo(4);
        assertThat(signInAt(NOW, "owner@example.com", "wrong password!")).isInstanceOf(Result.Locked.class);
    }

    private void lock() {
        for (int i = 0; i < 5; i++) {
            signInAt(NOW, "owner@example.com", "wrong password!");
        }
    }

    private Result signInAt(Instant now, String email, String password) {
        var service = new SignInAdministratorService(new InMemoryUserSignInStore(users), passwords,
            Clock.fixed(now, ZoneOffset.UTC));
        return service.execute(new Command(email, password));
    }
}
