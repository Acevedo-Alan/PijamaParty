package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.SecondFactorCode;
import com.pijamaparty.store.accounts.domain.TotpSecret;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;

/** As the other sign-in steps, it runs in the {@code SYSTEM} transaction of {@link UserSignInStore}, row locked. */
@Service
class ConfirmSecondFactorEnrollmentService implements ConfirmSecondFactorEnrollment {

    private final UserSignInStore users;
    private final RecoveryCodes recoveryCodes;
    private final RecoveryCodeGenerator codeGenerator;
    private final SecondFactorCipher cipher;
    private final TotpVerifier totp;
    private final Clock clock;

    ConfirmSecondFactorEnrollmentService(UserSignInStore users, RecoveryCodes recoveryCodes,
                                         RecoveryCodeGenerator codeGenerator, SecondFactorCipher cipher,
                                         TotpVerifier totp, Clock clock) {
        this.users = users;
        this.recoveryCodes = recoveryCodes;
        this.codeGenerator = codeGenerator;
        this.cipher = cipher;
        this.totp = totp;
        this.clock = clock;
    }

    @Override
    public Result execute(Command command) {
        Optional<TotpSecret> secret = cipher.decrypt(command.secret());
        if (secret.isEmpty()) {
            return new Result.NotPending();
        }
        Optional<SecondFactorCode> code = SecondFactorCode.parse(command.code());
        return users.withUserLockedForSignIn(command.administrator(), current -> confirm(current, command,
            secret.get(), code));
    }

    private Result confirm(Optional<User> current, Command command, TotpSecret secret,
                           Optional<SecondFactorCode> code) {
        Instant now = clock.instant();
        Optional<User> administrator = current.filter(user -> user.role() == UserRole.ADMIN);
        if (administrator.isEmpty()) {
            return new Result.NotPending();
        }
        User user = administrator.get();
        if (user.secondFactor().isPresent()) {
            return new Result.AlreadyEnrolled();
        }
        if (user.isLockedAt(now)) {
            return new Result.Locked(user.lockedUntil().orElseThrow());
        }
        // Only a code of the app proves the enrollment; a recovery code does not exist yet.
        OptionalLong step = code
            .filter(SecondFactorCode.Totp.class::isInstance)
            .map(value -> totp.matchingStep(secret, (SecondFactorCode.Totp) value, now))
            .orElse(OptionalLong.empty());
        if (step.isEmpty()) {
            return user.registerFailedSignIn(now)
                .<Result>map(Result.Locked::new)
                .orElseGet(Result.InvalidCode::new);
        }
        user.enrollSecondFactor(command.secret(), step.getAsLong(), now);
        List<SecondFactorCode.Recovery> codes = codeGenerator.newCodes();
        recoveryCodes.replace(user.id(), codes.stream().map(SecondFactorCode.Recovery::hash).toList());
        return new Result.Enrolled(AdministratorView.of(user),
            codes.stream().map(SecondFactorCode.Recovery::display).toList());
    }
}
