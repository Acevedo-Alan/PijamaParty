package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.AddressForm;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;
import java.util.Set;

/** UC-20: the signed-in customer adds an address to its book ({@code POST /api/account/addresses}). */
public interface AddAddress {

    Result execute(Command command);

    /** @param primary whether it becomes the primary; the first address always does */
    record Command(Actor actor, AddressForm address, boolean primary) {

        public Command {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(address, "address");
        }
    }

    sealed interface Result {

        record Added(AddressView address) implements Result {}

        /** Fields that break a rule of the address, each named once. */
        record InvalidAddress(Set<AddressForm.Field> fields) implements Result {

            public InvalidAddress {
                fields = Set.copyOf(fields);
            }
        }

        /** The book already has its {@value com.pijamaparty.store.accounts.domain.AddressBook#MAX_ADDRESSES}. */
        record AddressBookFull() implements Result {}

        /** The actor is not a customer, or its account no longer exists. */
        record NotSignedIn() implements Result {}
    }
}
