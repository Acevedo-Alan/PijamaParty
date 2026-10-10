package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.PasswordRules;
import com.pijamaparty.store.platform.domain.UserId;

/**
 * UC-04: the first administrator is created from the deployment settings when none exists. An existing
 * administrator is never changed by them.
 */
public interface CreateInitialAdministrator {

    Result execute(Command command);

    record Command(String email, String name, String password) {

        @Override
        public String toString() {
            return "Command[***]";
        }
    }

    sealed interface Result {

        record Created(UserId id) implements Result {}

        record AlreadyExists() implements Result {}

        record InvalidEmail() implements Result {}

        record InvalidName() implements Result {}

        record InvalidPassword(PasswordRules.Violation violation) implements Result {}
    }
}
