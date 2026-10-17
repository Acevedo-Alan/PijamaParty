package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;

/**
 * UC-27 (T-E4-15): the signed-in customer unlinks its account of a provider ({@code DELETE
 * /api/account/identities/{provider}}). The last way in cannot be unlinked: a password or another provider must
 * remain.
 */
public interface UnlinkExternalAccount {

    Result execute(Command command);

    record Command(Actor actor, ExternalProvider provider) {

        public Command {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(provider, "provider");
        }
    }

    sealed interface Result {

        record Unlinked() implements Result {}

        /** The customer has no account of that provider. */
        record NotLinked() implements Result {}

        /** No password and no other provider: it is refused, and the customer can create a password instead. */
        record LastSignInMethod() implements Result {}

        /** The actor is not a customer, or its account no longer exists. */
        record NotSignedIn() implements Result {}
    }
}
