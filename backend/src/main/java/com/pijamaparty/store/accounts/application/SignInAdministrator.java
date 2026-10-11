package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;

/**
 * UC-04, first step: the administrator's email and password. A right password never opens the back office by itself
 * (T-E1-16): it says which second-factor step comes next.
 */
public interface SignInAdministrator {

    Result execute(Command command);

    /** Raw input as typed: the use case normalizes the email and never stores or logs the password. */
    record Command(String email, String password) {

        @Override
        public String toString() {
            return "Command[***]";
        }
    }

    sealed interface Result {

        /** Right password: {@code nextStep} decides how the sign-in goes on, always for this user. */
        record PasswordAccepted(UserId administrator, NextStep nextStep) implements Result {}

        /** Unknown email, wrong password or not an administrator: the caller cannot tell which. */
        record InvalidCredentials() implements Result {}

        record Locked(Instant until) implements Result {}
    }

    enum NextStep {
        /** The administrator types a code of the authenticator app or a recovery code. */
        SECOND_FACTOR,
        /** No second factor yet: the administrator enrolls an authenticator app. */
        ENROLLMENT
    }
}
