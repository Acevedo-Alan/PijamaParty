package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-27 (T-E4-15): a signed-in customer comes back from a provider it chose to link from "Mi cuenta". Its session
 * proves the store account and the provider proves the external account, so the provider's email does not have to be
 * the account's. The provider's authentication was already checked by the adapter, and so was the session's customer.
 */
public interface LinkExternalAccount {

    Result execute(Command command);

    /**
     * What the provider returned. Personal data: never logged.
     *
     * @param email as the provider gave it, not normalized yet; empty when it gave none
     * @param emailVerified what the provider says of the email; only Google's counts (UC-27)
     */
    record Command(UserId customer, ExternalAccount account, Optional<String> email, boolean emailVerified) {

        public Command {
            Objects.requireNonNull(customer, "customer");
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(email, "email");
        }

        @Override
        public String toString() {
            return "Command[customer=" + customer + ", " + account + ", ***]";
        }
    }

    sealed interface Result {

        /** Linked now, or it already was to this customer. */
        record Linked() implements Result {}

        /** The external account is another customer's, or this one already has another account of that provider. */
        record AlreadyLinked() implements Result {}

        /** The customer's account no longer exists. */
        record NotFound() implements Result {}
    }
}
