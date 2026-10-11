package com.pijamaparty.store.accounts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T-E1-16: what the administrator types, and the second-factor rules of the user. */
class SecondFactorDomainTest {

    private static final Instant NOW = Instant.parse("2026-10-11T15:00:00Z");
    private static final EncryptedSecret SECRET = new EncryptedSecret(new byte[] {1, 2, 3});

    @Test
    void givenTypedCodes_whenParsed_thenSpacesDashesAndCaseDoNotMatter() {
        assertThat(SecondFactorCode.parse(" 123 456 ")).contains(new SecondFactorCode.Totp("123456"));
        assertThat(SecondFactorCode.parse("ab7k3-m9qx2")).contains(new SecondFactorCode.Recovery("AB7K3M9QX2"));
        assertThat(SecondFactorCode.parse("12345")).isEmpty();
        assertThat(SecondFactorCode.parse("1234567")).isEmpty();
        assertThat(SecondFactorCode.parse("ILOU0-00000")).as("letters left out of the alphabet").isEmpty();
        assertThat(SecondFactorCode.parse(null)).isEmpty();
    }

    @Test
    void givenARecoveryCode_whenShownAndHashed_thenItReadsAsTwoGroupsAndItsHashIsStable() {
        var code = new SecondFactorCode.Recovery("AB7K3M9QX2");

        assertThat(code.display()).isEqualTo("AB7K3-M9QX2");
        assertThat(code.hash()).isEqualTo(new SecondFactorCode.Recovery("AB7K3M9QX2").hash());
        assertThat(code.hash()).isNotEqualTo(new SecondFactorCode.Recovery("AB7K3M9QX3").hash());
        assertThat(code.toString()).doesNotContain("AB7K3");
    }

    @Test
    void givenSecrets_whenPrinted_thenTheyNeverShow() {
        assertThat(new TotpSecret("JBSWY3DPEHPK3PXP").toString()).doesNotContain("JBSWY3");
        assertThat(SECRET.toString()).doesNotContain("1");
        assertThatIllegalArgumentException().isThrownBy(() -> new TotpSecret("not base32!"));
    }

    @Test
    void givenAnAdministrator_whenEnrolled_thenTheConfirmingStepIsSpentAndLaterStepsAreAcceptedOnce() {
        User user = administrator();
        user.registerFailedSignIn(NOW);

        user.enrollSecondFactor(SECRET, 100, NOW);

        assertThat(user.failedSignInAttempts()).isZero();
        assertThat(user.acceptCodeAt(100, NOW)).isEqualTo(User.CodeOutcome.REPLAYED);
        assertThat(user.acceptCodeAt(101, NOW)).isEqualTo(User.CodeOutcome.ACCEPTED);
        assertThat(user.acceptCodeAt(101, NOW)).isEqualTo(User.CodeOutcome.REPLAYED);
        assertThat(user.secondFactor()).map(SecondFactor::lastStep).contains(101L);
    }

    @Test
    void givenAnEnrolledAdministratorOrACustomer_whenEnrolling_thenItIsRefused() {
        User user = administrator();
        user.enrollSecondFactor(SECRET, 1, NOW);
        User customer = User.restore(UserId.of(UUID.randomUUID()), Email.parse("ana@example.com").orElseThrow(), true,
            null, "Ana", UserRole.CUSTOMER, 0, null, NOW, NOW);

        assertThatIllegalStateException().isThrownBy(() -> user.enrollSecondFactor(SECRET, 2, NOW));
        assertThatIllegalStateException().isThrownBy(() -> customer.enrollSecondFactor(SECRET, 2, NOW));
    }

    @Test
    void givenAnEnrolledAdministrator_whenReset_thenItHasNoSecondFactorAndCanEnrollAgain() {
        User user = administrator();
        user.enrollSecondFactor(SECRET, 1, NOW);

        user.resetSecondFactor(NOW.plus(Duration.ofDays(1)));

        assertThat(user.secondFactor()).isEmpty();
        assertThat(user.updatedAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
        user.enrollSecondFactor(SECRET, 5, NOW);
        assertThat(user.secondFactor()).isPresent();
    }

    private static User administrator() {
        return User.newAdministrator(UserId.of(UUID.randomUUID()), Email.parse("owner@example.com").orElseThrow(),
            "Owner", new PasswordHash("{bcrypt}$2a$12$" + "a".repeat(53)), NOW.minus(Duration.ofDays(1)));
    }
}
