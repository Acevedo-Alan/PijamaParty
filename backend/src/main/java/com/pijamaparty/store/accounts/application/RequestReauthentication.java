package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;

/**
 * UC-28 (T-E4-16): the signed-in customer asks, from "Mi cuenta", to confirm the deletion of its account by signing in
 * again with a provider ({@code POST /api/account/oauth2/reauthenticate/{provider}}). Only a provider it has linked
 * can confirm it; the adapter then keeps the intent in the session and the storefront goes to the provider.
 */
public interface RequestReauthentication {

    Result execute(Command command);

    record Command(Actor actor, ExternalProvider provider) {

        public Command {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(provider, "provider");
        }
    }

    sealed interface Result {

        record Requested() implements Result {}

        /** The customer has no account of that provider. */
        record NotLinked() implements Result {}

        /** The actor is not a customer, or its account no longer exists. */
        record NotSignedIn() implements Result {}
    }
}
