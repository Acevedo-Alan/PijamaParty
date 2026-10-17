package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.platform.domain.Actor;
import java.util.List;

/** UC-20: the signed-in customer's address book ({@code GET /api/account/addresses}). */
public interface ListAddresses {

    Result execute(Actor actor);

    sealed interface Result {

        /** The primary first, then from the oldest to the newest. */
        record Listed(List<AddressView> addresses) implements Result {

            public Listed {
                addresses = List.copyOf(addresses);
            }
        }

        /** The actor is not a customer. */
        record NotSignedIn() implements Result {}
    }
}
