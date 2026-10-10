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
