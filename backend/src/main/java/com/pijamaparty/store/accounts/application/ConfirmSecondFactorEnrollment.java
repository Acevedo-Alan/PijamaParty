package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.EncryptedSecret;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * UC-04, enrollment (T-E1-16): a right code of the new authenticator app stores it as the administrator's second
 * factor, creates the recovery codes and opens the back office. A wrong code counts as a failed sign-in.
 */
public interface ConfirmSecondFactorEnrollment {

    Result execute(Command command);

    /** {@code administrator} and {@code secret} come from the pending sign-in, never from the request. */
    record Command(UserId administrator, EncryptedSecret secret, String code) {

        public Command {
            Objects.requireNonNull(administrator, "administrator");
            Objects.requireNonNull(secret, "secret");
        }

        @Override
        public String toString() {
            return "Command[***]";
        }
    }

    sealed interface Result {

        /** {@code recoveryCodes} are shown this once, as {@code XXXXX-XXXXX}; only their hashes are kept. */
        record Enrolled(AdministratorView administrator, List<String> recoveryCodes) implements Result {

            public Enrolled {
                recoveryCodes = List.copyOf(recoveryCodes);
            }

            @Override
            public String toString() {
                return "Enrolled[***]";
            }
        }

        /** Not 6 digits, or not a code of this secret now. */
        record InvalidCode() implements Result {}

        record Locked(Instant until) implements Result {}

        record AlreadyEnrolled() implements Result {}

        /** The user is gone, is not the administrator or the secret cannot be read: start again. */
        record NotPending() implements Result {}
    }
}
