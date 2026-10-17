package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.AddressId;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;

/**
 * UC-20: the signed-in customer deletes an address of its book ({@code DELETE /api/account/addresses/{id}}). Orders
 * keep their own copy of the address (R-04).
 */
public interface DeleteAddress {

    Result execute(Command command);

    record Command(Actor actor, AddressId id) {

        public Command {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(id, "id");
        }
    }

    sealed interface Result {

        record Deleted() implements Result {}

        /** Not in the customer's book: unknown, already deleted or another customer's. */
        record NotFound() implements Result {}

        /** The actor is not a customer, or its account no longer exists. */
        record NotSignedIn() implements Result {}
    }
}
