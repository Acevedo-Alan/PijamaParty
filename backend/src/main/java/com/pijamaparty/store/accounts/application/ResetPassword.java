package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.PasswordRules;
import java.util.Objects;

/**
 * UC-19 (T-E4-03): the link of the reset email is opened with a new password; the customer signs in with it
 * afterwards.
 */
public interface ResetPassword {

    Result execute(Command command);

    /** The token of the link and the new password: secrets, never logged. */
    record Command(String token, String password) {

        @Override
        public String toString() {
            return "Command[***]";
        }
    }

    sealed interface Result {

        record Reset() implements Result {}

        /** Unknown, used, expired or meant for something else: the caller cannot tell which. */
        record InvalidLink() implements Result {}

        /** The new password breaks the customer's rules; the link stays unused. */
        record InvalidPassword(PasswordRules.Violation violation) implements Result {

            public InvalidPassword {
                Objects.requireNonNull(violation, "violation");
            }
        }
    }
}
