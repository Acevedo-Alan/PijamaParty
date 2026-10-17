package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;

/**
 * UC-28 (T-E4-16): the signed-in customer deletes its account ({@code DELETE /api/account}), confirming it with its
 * password or by having just signed in again with a linked provider. Its addresses, external accounts and cart go
 * with it; its orders stay, unlinked, for the shop's legal duties; every session of the account ends.
 */
public interface DeleteCustomerAccount {

    Result execute(Command command);

    record Command(Actor actor, Confirmation confirmation) {

        public Command {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(confirmation, "confirmation");
        }
    }

    /** How the customer confirmed the deletion. */
    sealed interface Confirmation {

        /** The password it typed. */
        record Password(String password) implements Confirmation {

            public Password {
                Objects.requireNonNull(password, "password");
            }

            @Override
            public String toString() {
                return "Password[***]";
            }
        }

        /** The session holds a reauthentication with a linked provider of less than 5 minutes, checked by the adapter. */
        record Reauthenticated() implements Confirmation {}

        /** Neither. */
        record None() implements Confirmation {}
    }

    sealed interface Result {

        /** The account no longer exists; the adapter ends the request's own session too. */
        record Deleted() implements Result {}

        /** The password is not the account's, or the account has none. */
        record WrongPassword() implements Result {}

        /** No password and no recent reauthentication. */
        record ConfirmationRequired() implements Result {}

        /** The actor is not a customer, or its account no longer exists. */
        record NotSignedIn() implements Result {}
    }
}
