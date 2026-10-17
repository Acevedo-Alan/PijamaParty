package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Objects;

/**
 * UC-28 (T-E4-16): a signed-in customer comes back from a provider it chose, from "Mi cuenta", to confirm the deletion
 * of its account with. It counts only when the external account that came back is the one linked to this customer.
 * Nothing changes either way. The provider's authentication and the session's customer were already checked by the
 * adapter.
 */
public interface ReauthenticateWithExternalAccount {

    Result execute(Command command);

    record Command(UserId customer, ExternalAccount account) {

        public Command {
            Objects.requireNonNull(customer, "customer");
            Objects.requireNonNull(account, "account");
        }
    }

    sealed interface Result {

        record Reauthenticated() implements Result {}

        /** The external account is not linked, or it is linked to someone else. */
        record NotLinked() implements Result {}
    }
}
