package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.PasswordRules;
import com.pijamaparty.store.platform.domain.UserId;

/** UC-19: a visitor registers with email, password and name; the account waits for its email to be verified. */
public interface RegisterCustomer {

    Result execute(Command command);

    /** Raw input as typed: the use case normalizes the email and the name, and never stores or logs the password. */
    record Command(String email, String password, String name) {

        @Override
        public String toString() {
            return "Command[***]";
        }
    }

    sealed interface Result {

        /** Created unverified; the verification email is on its way. */
        record Registered(UserId customer) implements Result {}

        /** Says nothing else about the account that has that email (UC-19). */
        record EmailAlreadyRegistered() implements Result {}

        record InvalidEmail() implements Result {}

        record InvalidName() implements Result {}

        record InvalidPassword(PasswordRules.Violation violation) implements Result {}
    }
}
