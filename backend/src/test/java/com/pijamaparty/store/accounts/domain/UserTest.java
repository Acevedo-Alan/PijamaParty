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

    @Test
    void givenACustomer_whenItRemembersAPhone_thenItKeepsItAndTheSamePhoneChangesNothing() {
        User user = User.newCustomer(UserId.of(UUID.randomUUID()), email(), "Ana", hash(), NOW);
        Instant later = NOW.plus(Duration.ofDays(1));

        user.rememberPhone("3425551234", later);
        user.rememberPhone("3425551234", later.plus(Duration.ofDays(1)));

        assertThat(user.phone()).contains("3425551234");
        assertThat(user.updatedAt()).isEqualTo(later);
        assertThat(user.copy().phone()).contains("3425551234");
    }

    @Test
    void givenAPhoneThatIsNot8To15Digits_whenRemembered_thenItIsRefused() {
        User user = User.newCustomer(UserId.of(UUID.randomUUID()), email(), "Ana", hash(), NOW);

        for (String phone : new String[] {"1234567", "1234567890123456", "342 555-1234", ""}) {
            assertThatThrownBy(() -> user.rememberPhone(phone, NOW)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(user.phone()).isEmpty();
    }

    @Test
    void givenTheAdministrator_whenItRemembersAPhone_thenItIsRefused() {
        assertThatThrownBy(() -> administrator().rememberPhone("3425551234", NOW))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void givenAProvider_whenItCreatesACustomer_thenItHasNoPasswordAndIsVerifiedOnlyIfTheProviderVouched() {
        User google = User.newExternalCustomer(UserId.of(UUID.randomUUID()), email(), "Ana", true, NOW);
        User facebook = User.newExternalCustomer(UserId.of(UUID.randomUUID()), email(), "Ana", false, NOW);

        assertThat(google.passwordHash()).isEmpty();
        assertThat(google.emailVerified()).isTrue();
        assertThat(google.role()).isEqualTo(UserRole.CUSTOMER);
        assertThat(facebook.emailVerified()).isFalse();
    }

    @Test
    void givenTheNameAProviderGave_whenNamingTheCustomer_thenItIsTrimmedCutTo100OrTakenFromTheEmail() {
        Email ana = Email.parse("ana.perez@example.com").orElseThrow();

        assertThat(User.nameFromProvider("  Ana Pérez  ", ana)).isEqualTo("Ana Pérez");
        assertThat(User.nameFromProvider("   ", ana)).isEqualTo("ana.perez");
        assertThat(User.nameFromProvider(null, ana)).isEqualTo("ana.perez");
        assertThat(User.nameFromProvider("ñ".repeat(150), ana)).hasSize(100);
        assertThat(User.isValidName(User.nameFromProvider("a".repeat(99) + " b", ana))).isTrue();
    }

    @Test
    void givenAnUnverifiedCustomerWithAPassword_whenVerifiedThroughAnExternalAccount_thenItLosesThePassword() {
        User customer = User.newCustomer(UserId.of(UUID.randomUUID()), email(), "Ana", hash(), NOW);
        Instant later = NOW.plusSeconds(60);

        customer.verifyEmailThroughExternalAccount(later);

        assertThat(customer.emailVerified()).isTrue();
        assertThat(customer.passwordHash()).isEmpty();
        assertThat(customer.updatedAt()).isEqualTo(later);
    }

    @Test
    void givenAVerifiedCustomer_whenVerifiedThroughAnExternalAccount_thenNothingChanges() {
        User customer = User.newCustomer(UserId.of(UUID.randomUUID()), email(), "Ana", hash(), NOW);
        customer.verifyEmail(NOW);

        customer.verifyEmailThroughExternalAccount(NOW.plusSeconds(60));

        assertThat(customer.passwordHash()).contains(hash());
        assertThat(customer.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void givenTheAdministrator_whenVerifiedThroughAnExternalAccount_thenItIsRefused() {
        assertThatThrownBy(() -> administrator().verifyEmailThroughExternalAccount(NOW))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void givenACustomerWithoutAPassword_whenCreatingOne_thenItHasItAndStaysAsVerifiedAsItWas() {
        User customer = User.newExternalCustomer(UserId.of(UUID.randomUUID()), email(), "Ana", false, NOW);
        Instant later = NOW.plusSeconds(60);

        assertThat(customer.createPassword(hash(), later)).isTrue();

        assertThat(customer.passwordHash()).contains(hash());
        assertThat(customer.emailVerified()).isFalse();
        assertThat(customer.updatedAt()).isEqualTo(later);
    }

    @Test
    void givenACustomerWithAPassword_whenCreatingAnother_thenNothingChanges() {
        User customer = User.newCustomer(UserId.of(UUID.randomUUID()), email(), "Ana", hash(), NOW);
        var other = new PasswordHash("{test}other");

        assertThat(customer.createPassword(other, NOW.plusSeconds(60))).isFalse();

        assertThat(customer.passwordHash()).contains(hash());
        assertThat(customer.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void givenTheAdministrator_whenCreatingAPassword_thenItIsRefused() {
        assertThatThrownBy(() -> administrator().createPassword(hash(), NOW))
            .isInstanceOf(IllegalStateException.class);
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
