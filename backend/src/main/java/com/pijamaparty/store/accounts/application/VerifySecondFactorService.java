package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.SecondFactor;
import com.pijamaparty.store.accounts.domain.SecondFactorCode;
import com.pijamaparty.store.accounts.domain.TotpSecret;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.activity.api.ActivityAction;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;

/**
 * Not {@code @Transactional}: as the password step, it runs in the {@code SYSTEM} transaction of
 * {@link UserSignInStore}, with the user's row locked, so two codes for one account are judged one after the other
 * and a recovery code is consumed in the same transaction that opens the sign-in.
 */
@Service
class VerifySecondFactorService implements VerifySecondFactor {

    private final UserSignInStore users;
    private final RecoveryCodes recoveryCodes;
    private final SecondFactorCipher cipher;
    private final TotpVerifier totp;
    private final AccountActivity activity;
    private final Clock clock;

    VerifySecondFactorService(UserSignInStore users, RecoveryCodes recoveryCodes, SecondFactorCipher cipher,
                              TotpVerifier totp, AccountActivity activity, Clock clock) {
        this.users = users;
        this.recoveryCodes = recoveryCodes;
        this.cipher = cipher;
        this.totp = totp;
        this.activity = activity;
        this.clock = clock;
    }

    @Override
    public Result execute(Command command) {
        Optional<SecondFactorCode> code = SecondFactorCode.parse(command.code());
        return users.withUserLockedForSignIn(command.administrator(), current -> verify(current, code));
    }

    private Result verify(Optional<User> current, Optional<SecondFactorCode> code) {
        Instant now = clock.instant();
        Optional<User> administrator = current
            .filter(user -> user.role() == UserRole.ADMIN && user.secondFactor().isPresent());
        if (administrator.isEmpty()) {
            return new Result.NotPending();
        }
        User user = administrator.get();
        if (user.isLockedAt(now)) {
            return new Result.Locked(user.lockedUntil().orElseThrow());
        }
        boolean accepted = code.map(value -> switch (value) {
            case SecondFactorCode.Totp digits -> acceptTotp(user, digits, now);
            case SecondFactorCode.Recovery recovery -> acceptRecovery(user, recovery, now);
        }).orElse(false);
        if (accepted) {
            // UC-37: every sign-in is recorded, and a recovery code used for it besides.
            activity.record(AdministratorEntries.entry(user.id(), ActivityAction.ADMIN_SIGNED_IN));
            if (code.get() instanceof SecondFactorCode.Recovery) {
                activity.record(AdministratorEntries.entry(user.id(), ActivityAction.RECOVERY_CODE_USED));
            }
            return new Result.SignedIn(AdministratorView.of(user));
        }
        return user.registerFailedSignIn(now)
            .<Result>map(Result.Locked::new)
            .orElseGet(Result.InvalidCode::new);
    }

    private boolean acceptTotp(User user, SecondFactorCode.Totp code, Instant now) {
        SecondFactor secondFactor = user.secondFactor().orElseThrow();
        Optional<TotpSecret> secret = cipher.decrypt(secondFactor.secret());
        if (secret.isEmpty()) {
            // A secret this key cannot read (key changed): nothing can match; a reset enrolls again.
            return false;
        }
        OptionalLong step = totp.matchingStep(secret.get(), code, now);
        return step.isPresent() && user.acceptCodeAt(step.getAsLong(), now) == User.CodeOutcome.ACCEPTED;
    }

    private boolean acceptRecovery(User user, SecondFactorCode.Recovery code, Instant now) {
        if (!recoveryCodes.consume(user.id(), code.hash(), now)) {
            return false;
        }
        user.registerSuccessfulSignIn(now);
        return true;
    }
}
