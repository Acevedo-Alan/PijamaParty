package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.PasswordRules;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;

/**
 * UC-27 (T-E4-15): a signed-in customer without a password creates one from "Mi cuenta" ({@code PUT
 * /api/account/password}). The session is enough. Changing an existing password stays with the recovery of UC-19.
 */
public interface CreatePassword {

    Result execute(Command command);

    /** @param password personal data: never logged */
    record Command(Actor actor, String password) {

        public Command {
            Objects.requireNonNull(actor, "actor");
        }

        @Override
        public String toString() {
            return "Command[actor=" + actor + ", ***]";
        }
    }

    sealed interface Result {

        record Created() implements Result {}

        /** The customer already has a password: nothing changes. */
        record AlreadySet() implements Result {}

        /** The customer's password rules of UC-19. */
        record InvalidPassword(PasswordRules.Violation violation) implements Result {}

        /** The actor is not a customer, or its account no longer exists. */
        record NotSignedIn() implements Result {}
    }
}
