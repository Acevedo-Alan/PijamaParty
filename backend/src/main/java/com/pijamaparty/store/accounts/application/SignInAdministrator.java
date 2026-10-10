package com.pijamaparty.store.accounts.application;

import java.time.Instant;

/** UC-04: the administrator signs in to the back office with email and password. */
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

        record SignedIn(AdministratorView administrator) implements Result {}

        /** Unknown email, wrong password or not an administrator: the caller cannot tell which. */
        record InvalidCredentials() implements Result {}

        record Locked(Instant until) implements Result {}
    }
}
