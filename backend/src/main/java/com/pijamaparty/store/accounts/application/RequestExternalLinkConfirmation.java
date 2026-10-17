package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.ExternalAccount;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-27 (T-E4-14): the provider gave no email (a Facebook account registered with a phone), so the visitor gives one,
 * which is confirmed by email before the external account is linked to the account of that email.
 */
public interface RequestExternalLinkConfirmation {

    Result execute(Command command);

    /**
     * Personal data: never logged.
     *
     * @param name as the provider gave it; empty when it gave none
     * @param email as typed
     */
    record Command(ExternalAccount account, Optional<String> name, String email) {

        public Command {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(name, "name");
        }

        @Override
        public String toString() {
            return "Command[" + account + ", ***]";
        }
    }

    sealed interface Result {

        /** The same answer whether an account has the email or not, and whether an email was sent or not. */
        record Requested() implements Result {}

        record InvalidEmail() implements Result {}
    }
}
