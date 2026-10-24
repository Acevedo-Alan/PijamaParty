package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.application.InMemorySecondFactorPorts.InMemoryEnrollmentQrCode;
import com.pijamaparty.store.accounts.application.InMemorySecondFactorPorts.InMemoryRecoveryCodeGenerator;
import com.pijamaparty.store.accounts.application.InMemorySecondFactorPorts.InMemoryRecoveryCodes;
import com.pijamaparty.store.accounts.application.InMemorySecondFactorPorts.InMemorySecondFactorCipher;
import com.pijamaparty.store.accounts.application.InMemorySecondFactorPorts.InMemoryTotpSecretGenerator;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.EncryptedSecret;
import com.pijamaparty.store.accounts.domain.TotpSecret;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** UC-04, T-E1-16: the second factor after the password, its enrollment and its reset. */
class SecondFactorServicesTest {

    private static final Instant NOW = Instant.parse("2026-10-11T15:00:10Z");
    private static final long STEP = InMemoryTotpVerifier.stepAt(NOW);

    private final InMemoryUserStore users = new InMemoryUserStore();
    private final InMemoryUserSignInStore signInStore = new InMemoryUserSignInStore(users);
    private final InMemoryRecoveryCodes recoveryCodes = new InMemoryRecoveryCodes();
    private final InMemorySecondFactorCipher cipher = new InMemorySecondFactorCipher();
    private final InMemoryTotpVerifier totp = new InMemoryTotpVerifier();
    private final InMemoryPasswordHasher passwords = new InMemoryPasswordHasher();
    private final InMemoryAccountActivity activity = new InMemoryAccountActivity();
    private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private User administrator;

    @BeforeEach
    void anAdministrator() {
        administrator = User.newAdministrator(UserId.of(UUID.randomUUID()),
            Email.parse("owner@example.com").orElseThrow(), "Owner", passwords.hash("correct horse battery"),
            NOW.minus(Duration.ofDays(1)));
        users.save(administrator);
    }

    @Test
    void givenNoSecondFactor_whenEnrollmentStarts_thenANewSecretComesAsTextUriAndQrCode() {
        var started = (StartSecondFactorEnrollment.Result.Started) start();

        assertThat(started.otpauthUri()).isEqualTo("otpauth://totp/Pijama%20Party:owner%40example.com?secret="
            + started.secretText() + "&issuer=Pijama%20Party&algorithm=SHA1&digits=6&period=30");
        assertThat(started.qrCode()).isEqualTo("data:image/png;base64,QR(" + started.otpauthUri() + ")");
        assertThat(cipher.decrypt(started.secret())).map(TotpSecret::base32).contains(started.secretText());
        assertThat(((StartSecondFactorEnrollment.Result.Started) start()).secretText())
            .isNotEqualTo(started.secretText());
        assertThat(users.findById(administrator.id()).orElseThrow().secondFactor()).isEmpty();
    }

    @Test
    void givenAStartedEnrollment_whenConfirmedWithTheCodeOfTheApp_thenItIsStoredWith10RecoveryCodesShownOnce() {
        var started = (StartSecondFactorEnrollment.Result.Started) start();

        var result = confirm(started.secret(), appCode(started, STEP));

        var enrolled = (ConfirmSecondFactorEnrollment.Result.Enrolled) result;
        assertThat(enrolled.administrator().id()).isEqualTo(administrator.id());
        assertThat(enrolled.recoveryCodes()).hasSize(10).allMatch(code -> code.matches("[0-9A-Z]{5}-[0-9A-Z]{5}"));
        assertThat(recoveryCodes.unused(administrator.id())).isEqualTo(10);
        User stored = users.findById(administrator.id()).orElseThrow();
        assertThat(stored.secondFactor()).hasValueSatisfying(factor -> {
            assertThat(factor.secret()).isEqualTo(started.secret());
            assertThat(factor.lastStep()).isEqualTo(STEP);
        });
        assertThat(start()).isEqualTo(new StartSecondFactorEnrollment.Result.AlreadyEnrolled());
    }

    @Test
    void givenAStartedEnrollment_whenConfirmedWithAWrongCode_thenNothingIsStoredAndItCountsAsAFailure() {
        var started = (StartSecondFactorEnrollment.Result.Started) start();

        assertThat(confirm(started.secret(), "000000")).isEqualTo(new ConfirmSecondFactorEnrollment.Result.InvalidCode());
        assertThat(confirm(started.secret(), "AAAAA-00000")).isEqualTo(
            new ConfirmSecondFactorEnrollment.Result.InvalidCode());

        User stored = users.findById(administrator.id()).orElseThrow();
        assertThat(stored.secondFactor()).isEmpty();
        assertThat(stored.failedSignInAttempts()).isEqualTo(2);
    }

    @Test
    void givenASecretThisCipherDidNotMake_whenConfirmed_thenTheEnrollmentMustStartAgain() {
        assertThat(confirm(new EncryptedSecret(new byte[] {1, 2}), "123456"))
            .isEqualTo(new ConfirmSecondFactorEnrollment.Result.NotPending());
    }

    @Test
    void givenAnEnrolledAdministrator_whenTheCodeOfTheAppIsTyped_thenTheBackOfficeOpensAndTheFailuresReset() {
        TotpSecret secret = enrolledAt(STEP - 4);
        failOnce();

        var result = verify(InMemoryTotpVerifier.codeAt(secret, STEP));

        assertThat(result).isEqualTo(new VerifySecondFactor.Result.SignedIn(
            new AdministratorView(administrator.id(), "owner@example.com", "Owner")));
        assertThat(users.findById(administrator.id()).orElseThrow().failedSignInAttempts()).isZero();
    }

    @Test
    void givenAUsedCode_whenTypedAgain_thenItIsRefusedAndCountsAsAFailure() {
        TotpSecret secret = enrolledAt(STEP - 4);
        String code = InMemoryTotpVerifier.codeAt(secret, STEP);
        verify(code);

        assertThat(verify(code)).isEqualTo(new VerifySecondFactor.Result.InvalidCode());
        assertThat(verify(InMemoryTotpVerifier.codeAt(secret, STEP - 1)))
            .as("an earlier step than the last accepted one").isEqualTo(new VerifySecondFactor.Result.InvalidCode());
        assertThat(users.findById(administrator.id()).orElseThrow().failedSignInAttempts()).isEqualTo(2);
    }

    @Test
    void givenFiveWrongCodes_whenTheFifthArrives_thenTheAccountIsLockedAndARightCodeWaits() {
        TotpSecret secret = enrolledAt(STEP - 4);
        for (int i = 0; i < 4; i++) {
            assertThat(verify("not a code")).isEqualTo(new VerifySecondFactor.Result.InvalidCode());
        }

        assertThat(verify("999999")).isEqualTo(new VerifySecondFactor.Result.Locked(NOW.plus(Duration.ofMinutes(15))));
        assertThat(verify(InMemoryTotpVerifier.codeAt(secret, STEP)))
            .isEqualTo(new VerifySecondFactor.Result.Locked(NOW.plus(Duration.ofMinutes(15))));
    }

    @Test
    void givenARecoveryCode_whenUsedInsteadOfACode_thenItOpensOnceInAnyCaseOrSpacing() {
        var started = (StartSecondFactorEnrollment.Result.Started) start();
        var enrolled = (ConfirmSecondFactorEnrollment.Result.Enrolled) confirm(started.secret(), appCode(started, STEP));
        String code = enrolled.recoveryCodes().getFirst();

        assertThat(verify(" " + code.toLowerCase() + " ")).isInstanceOf(VerifySecondFactor.Result.SignedIn.class);
        assertThat(verify(code)).isEqualTo(new VerifySecondFactor.Result.InvalidCode());
        assertThat(recoveryCodes.unused(administrator.id())).isEqualTo(9);
    }

    // --- UC-37: the activity log ---

    @Test
    void givenAStartedEnrollment_whenConfirmed_thenTheSignInAndTheEnrollmentAreRecorded() {
        var started = (StartSecondFactorEnrollment.Result.Started) start();

        confirm(started.secret(), "000000");
        confirm(started.secret(), appCode(started, STEP));

        assertThat(activity.entries()).containsExactly(signInEntry(ActivityAction.ADMIN_SIGNED_IN),
            signInEntry(ActivityAction.SECOND_FACTOR_ENROLLED));
    }

    @Test
    void givenAnEnrolledAdministrator_whenAWrongAndThenARightCodeOfTheAppAreTyped_thenOnlyTheSignInIsRecorded() {
        TotpSecret secret = enrolledAt(STEP - 4);
        activity.clear();

        failOnce();
        verify(InMemoryTotpVerifier.codeAt(secret, STEP));

        assertThat(activity.single()).isEqualTo(signInEntry(ActivityAction.ADMIN_SIGNED_IN));
    }

    @Test
    void givenARecoveryCode_whenItOpensTheBackOffice_thenTheSignInAndTheUsedCodeAreRecordedButNeverTheCode() {
        var started = (StartSecondFactorEnrollment.Result.Started) start();
        var enrolled = (ConfirmSecondFactorEnrollment.Result.Enrolled) confirm(started.secret(), appCode(started, STEP));
        activity.clear();

        verify(enrolled.recoveryCodes().getFirst());

        assertThat(activity.entries()).containsExactly(signInEntry(ActivityAction.ADMIN_SIGNED_IN),
            signInEntry(ActivityAction.RECOVERY_CODE_USED));
    }

    private ActivityEntry signInEntry(ActivityAction action) {
        return new ActivityEntry(administrator.id(), action,
            ActivitySubject.of(SubjectType.ADMINISTRATOR, administrator.id().value()));
    }

    @Test
    void givenNoSecondFactorOrNoAdministrator_whenACodeIsTyped_thenThereIsNoSignInToFinish() {
        assertThat(verify("123456")).isEqualTo(new VerifySecondFactor.Result.NotPending());
        var customer = User.restore(UserId.of(UUID.randomUUID()), Email.parse("ana@example.com").orElseThrow(), true,
            passwords.hash("correct horse battery"), "Ana", UserRole.CUSTOMER, 0, null, NOW, NOW);
        users.save(customer);

        assertThat(new VerifySecondFactorService(signInStore, recoveryCodes, cipher, totp, activity, clock)
            .execute(new VerifySecondFactor.Command(customer.id(), "123456")))
            .isEqualTo(new VerifySecondFactor.Result.NotPending());
        assertThat(new StartSecondFactorEnrollmentService(signInStore, new InMemoryTotpSecretGenerator(), cipher,
            new InMemoryEnrollmentQrCode()).execute(new StartSecondFactorEnrollment.Command(customer.id())))
            .isEqualTo(new StartSecondFactorEnrollment.Result.NotPending());
    }

    @Test
    void givenAnEnrolledAdministrator_whenReset_thenTheSecondFactorAndItsCodesAreGoneAndTheNextSignInEnrolls() {
        enrolledAt(STEP - 4);
        recoveryCodes.replace(administrator.id(), List.of());
        var reset = new ResetAdministratorSecondFactorService(users, users, recoveryCodes, clock);

        assertThat(reset.execute()).isEqualTo(new ResetAdministratorSecondFactor.Result.Reset());
        assertThat(reset.execute()).isEqualTo(new ResetAdministratorSecondFactor.Result.NothingToReset());
        assertThat(users.findById(administrator.id()).orElseThrow().secondFactor()).isEmpty();
        assertThat(start()).isInstanceOf(StartSecondFactorEnrollment.Result.Started.class);
    }

    /** Enrolls through the use cases at an earlier step, and returns the secret the app holds. */
    private TotpSecret enrolledAt(long step) {
        Clock now = clock;
        clock = Clock.fixed(Instant.ofEpochSecond(step * InMemoryTotpVerifier.PERIOD_SECONDS), ZoneOffset.UTC);
        var started = (StartSecondFactorEnrollment.Result.Started) start();
        confirm(started.secret(), appCode(started, step));
        clock = now;
        return new TotpSecret(started.secretText());
    }

    private void failOnce() {
        verify("000000");
    }

    private static String appCode(StartSecondFactorEnrollment.Result.Started started, long step) {
        return InMemoryTotpVerifier.codeAt(new TotpSecret(started.secretText()), step);
    }

    private StartSecondFactorEnrollment.Result start() {
        return new StartSecondFactorEnrollmentService(signInStore, secretGenerator, cipher, new InMemoryEnrollmentQrCode())
            .execute(new StartSecondFactorEnrollment.Command(administrator.id()));
    }

    private final InMemoryTotpSecretGenerator secretGenerator = new InMemoryTotpSecretGenerator();
    private final InMemoryRecoveryCodeGenerator codeGenerator = new InMemoryRecoveryCodeGenerator();

    private ConfirmSecondFactorEnrollment.Result confirm(EncryptedSecret secret, String code) {
        return new ConfirmSecondFactorEnrollmentService(signInStore, recoveryCodes, codeGenerator, cipher, totp,
            activity, clock)
            .execute(new ConfirmSecondFactorEnrollment.Command(administrator.id(), secret, code));
    }

    private VerifySecondFactor.Result verify(String code) {
        return new VerifySecondFactorService(signInStore, recoveryCodes, cipher, totp, activity, clock)
            .execute(new VerifySecondFactor.Command(administrator.id(), code));
    }
}
