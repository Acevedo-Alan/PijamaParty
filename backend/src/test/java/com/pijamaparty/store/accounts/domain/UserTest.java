package com.pijamaparty.store.accounts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-10-10T15:00:00Z");

    @Test
    void givenFourConsecutiveFailures_whenTheFifthHappens_thenTheAccountIsLockedFor15Minutes() {
        User user = administrator();
        for (int i = 0; i < 4; i++) {
            assertThat(user.registerFailedSignIn(NOW)).isEmpty();
        }

        assertThat(user.registerFailedSignIn(NOW)).contains(NOW.plus(Duration.ofMinutes(15)));
        assertThat(user.isLockedAt(NOW.plus(Duration.ofMinutes(14)))).isTrue();
        assertThat(user.isLockedAt(NOW.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    void givenFailuresFollowedByASuccess_whenFailingAgain_thenOnlyConsecutiveFailuresCount() {
        User user = administrator();
        for (int i = 0; i < 4; i++) {
            user.registerFailedSignIn(NOW);
        }
        user.registerSuccessfulSignIn(NOW);

        for (int i = 0; i < 4; i++) {
            assertThat(user.registerFailedSignIn(NOW)).isEmpty();
        }
        assertThat(user.failedSignInAttempts()).isEqualTo(4);
    }

    @Test
    void givenAnExpiredLock_whenFailingAgain_thenThereAreFiveAttemptsBeforeTheNextLock() {
        User user = administrator();
        for (int i = 0; i < 5; i++) {
            user.registerFailedSignIn(NOW);
        }
        Instant afterLock = NOW.plus(Duration.ofMinutes(16));

        for (int i = 0; i < 4; i++) {
            assertThat(user.registerFailedSignIn(afterLock)).isEmpty();
        }
        assertThat(user.registerFailedSignIn(afterLock)).isPresent();
    }

    @Test
    void givenALockedAccount_whenRecordingAnAttempt_thenItIsRefusedSoTheLockIsNeverExtended() {
        User user = administrator();
        for (int i = 0; i < 5; i++) {
            user.registerFailedSignIn(NOW);
        }

        assertThatThrownBy(() -> user.registerFailedSignIn(NOW.plusSeconds(60)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> user.registerSuccessfulSignIn(NOW.plusSeconds(60)))
            .isInstanceOf(IllegalStateException.class);
        assertThat(user.lockedUntil()).contains(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void givenAnAdministrator_whenCreatedWithoutPassword_thenItIsRejected() {
        assertThatThrownBy(() -> User.newAdministrator(UserId.of(UUID.randomUUID()), email(), "Owner", null, NOW))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void givenANameWithSurroundingSpacesOrTooLong_whenCreatingAUser_thenItIsRejected() {
        assertThatThrownBy(() -> User.newAdministrator(UserId.of(UUID.randomUUID()), email(), " Owner", hash(), NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> User.newAdministrator(UserId.of(UUID.randomUUID()), email(), "x".repeat(101), hash(),
            NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenANewCustomer_whenCreated_thenItIsUnverifiedWithItsPassword() {
        User customer = User.newCustomer(UserId.of(UUID.randomUUID()), email(), "Ana", hash(), NOW);

        assertThat(customer.role()).isEqualTo(UserRole.CUSTOMER);
        assertThat(customer.emailVerified()).isFalse();
        assertThat(customer.passwordHash()).contains(hash());
        assertThat(customer.secondFactor()).isEmpty();
        assertThatThrownBy(() -> User.newCustomer(UserId.of(UUID.randomUUID()), email(), "Ana", null, NOW))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void givenAnUnverifiedCustomer_whenItsEmailIsVerifiedTwice_thenItIsVerifiedOnceAtTheFirstTime() {
        User customer = User.newCustomer(UserId.of(UUID.randomUUID()), email(), "Ana", hash(), NOW);

        customer.verifyEmail(NOW.plusSeconds(60));
        customer.verifyEmail(NOW.plusSeconds(120));

        assertThat(customer.emailVerified()).isTrue();
        assertThat(customer.updatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(customer.copy().emailVerified()).isTrue();
    }

    @Test
    void givenALockedUnverifiedCustomer_whenItResetsItsPassword_thenThePasswordIsReplacedTheLockClearedAndTheEmailVerified() {
        User customer = User.newCustomer(UserId.of(UUID.randomUUID()), email(), "Ana", hash(), NOW);
        for (int i = 0; i < User.MAX_CONSECUTIVE_FAILURES; i++) {
            customer.registerFailedSignIn(NOW);
        }
        customer.registerFailedSignIn(NOW.plus(User.LOCK_DURATION));
        var newHash = new PasswordHash("{test}new");
        Instant resetAt = NOW.plus(User.LOCK_DURATION).plusSeconds(60);

        customer.resetPassword(newHash, resetAt);

        assertThat(customer.passwordHash()).contains(newHash);
        assertThat(customer.failedSignInAttempts()).isZero();
        assertThat(customer.lockedUntil()).isEmpty();
        assertThat(customer.emailVerified()).isTrue();
        assertThat(customer.updatedAt()).isEqualTo(resetAt);
        assertThat(customer.copy().passwordHash()).contains(newHash);
    }

    @Test
    void givenACustomerLockedRightNow_whenItResetsItsPassword_thenItCanSignInAtOnce() {
        User customer = User.newCustomer(UserId.of(UUID.randomUUID()), email(), "Ana", hash(), NOW);
        for (int i = 0; i < User.MAX_CONSECUTIVE_FAILURES; i++) {
            customer.registerFailedSignIn(NOW);
        }

        customer.resetPassword(new PasswordHash("{test}new"), NOW.plusSeconds(60));

        assertThat(customer.isLockedAt(NOW.plusSeconds(60))).isFalse();
    }

    @Test
    void givenTheAdministrator_whenItResetsItsPasswordByEmail_thenItIsRefused() {
        User administrator = administrator();

        assertThatThrownBy(() -> administrator.resetPassword(new PasswordHash("{test}new"), NOW))
            .isInstanceOf(IllegalStateException.class);
        assertThat(administrator.passwordHash()).contains(hash());
    }

    @Test
    void givenAUser_whenCopied_thenChangesToTheCopyDoNotAffectTheOriginal() {
        User user = administrator();
        User copy = user.copy();

        copy.registerFailedSignIn(NOW);

        assertThat(user.failedSignInAttempts()).isZero();
    }

    private static User administrator() {
        return User.newAdministrator(UserId.of(UUID.randomUUID()), email(), "Owner", hash(), NOW);
    }

    private static Email email() {
        return Email.parse("owner@example.com").orElseThrow();
    }

    private static PasswordHash hash() {
        return new PasswordHash("{test}hash");
    }
}
