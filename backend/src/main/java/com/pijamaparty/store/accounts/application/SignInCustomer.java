package com.pijamaparty.store.accounts.application;

import java.time.Instant;

/** UC-19: a customer signs in with email and password, once its email is verified. */
public interface SignInCustomer {

    Result execute(Command command);

    /** Raw input as typed: the use case normalizes the email and never stores or logs the password. */
    record Command(String email, String password) {

        @Override
        public String toString() {
            return "Command[***]";
        }
    }

    sealed interface Result {

        record SignedIn(CustomerView customer) implements Result {}

        /** Unknown email, wrong password or not a customer: the caller cannot tell which. */
        record InvalidCredentials() implements Result {}

        record Locked(Instant until) implements Result {}

        /** Right password, email not verified yet: a new verification email is on its way, unless one is waiting. */
        record EmailNotVerified() implements Result {}
    }
}
