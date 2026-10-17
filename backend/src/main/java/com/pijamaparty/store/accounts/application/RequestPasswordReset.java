package com.pijamaparty.store.accounts.application;

/**
 * UC-19 (T-E4-03): someone forgot its password and asks for a reset link. The answer never says whether the email
 * belongs to an account.
 */
public interface RequestPasswordReset {

    Result execute(Command command);

    /** The email as typed: personal data, never logged. */
    record Command(String email) {

        @Override
        public String toString() {
            return "Command[***]";
        }
    }

    sealed interface Result {

        /** The only answer: a customer with that email gets the reset email; anyone else, nothing. */
        record Requested() implements Result {}
    }
}
