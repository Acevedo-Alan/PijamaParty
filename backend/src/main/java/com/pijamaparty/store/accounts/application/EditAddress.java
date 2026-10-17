package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.AddressForm;
import com.pijamaparty.store.accounts.domain.AddressId;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;
import java.util.Set;

/** UC-20: the signed-in customer replaces an address of its book ({@code PUT /api/account/addresses/{id}}). */
public interface EditAddress {

    Result execute(Command command);

    /** @param markPrimary true makes it the primary; false leaves it as it was */
    record Command(Actor actor, AddressId id, AddressForm address, boolean markPrimary) {

        public Command {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(address, "address");
        }
    }

    sealed interface Result {

        record Edited(AddressView address) implements Result {}

        /** Fields that break a rule of the address, each named once. */
        record InvalidAddress(Set<AddressForm.Field> fields) implements Result {

            public InvalidAddress {
                fields = Set.copyOf(fields);
            }
        }

        /** Not in the customer's book: unknown, deleted or another customer's. */
        record NotFound() implements Result {}

        /** The actor is not a customer, or its account no longer exists. */
        record NotSignedIn() implements Result {}
    }
}
