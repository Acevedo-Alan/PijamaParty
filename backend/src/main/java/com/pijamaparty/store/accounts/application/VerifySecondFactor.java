package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * UC-04, second step (T-E1-16): after a right password, a code of the administrator's authenticator app or an unused
 * recovery code opens the back office. A wrong code counts as a failed sign-in.
 */
public interface VerifySecondFactor {

    Result execute(Command command);

    /** {@code administrator} comes from the pending sign-in of the password step, never from the request. */
    record Command(UserId administrator, String code) {

        public Command {
            Objects.requireNonNull(administrator, "administrator");
        }

        @Override
        public String toString() {
            return "Command[***]";
        }
    }

    sealed interface Result {

        record SignedIn(AdministratorView administrator) implements Result {}

        /** Not a code, a wrong or reused one, or a used recovery code: the caller cannot tell which. */
        record InvalidCode() implements Result {}

        record Locked(Instant until) implements Result {}

        /** The user is gone, is not the administrator or has no second factor: start again with the password. */
        record NotPending() implements Result {}
    }
}
