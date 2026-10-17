package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-27 (T-E4-14): a visitor comes back from a provider, which identified its account. The provider's authentication
 * (signature, {@code state}, {@code nonce}) was already checked by the adapter.
 */
public interface SignInWithExternalAccount {

    Result execute(Command command);

    /**
     * What the provider returned. Personal data: never logged.
     *
     * @param email as the provider gave it, not normalized yet; empty when it gave none
     * @param emailVerified what the provider says of the email; only Google's counts (UC-27)
     * @param name as the provider gave it; empty when it gave none
     */
    record Command(ExternalAccount account, Optional<String> email, boolean emailVerified, Optional<String> name) {

        public Command {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(email, "email");
            Objects.requireNonNull(name, "name");
        }

        @Override
        public String toString() {
            return "Command[" + account + ", ***]";
        }
    }

    sealed interface Result {

        /** The customer signs in: its session opens now. */
        record SignedIn(UserId customer) implements Result {}

        /** A customer has the email the provider does not vouch for: the link confirmation email is on its way. */
        record LinkConfirmationSent() implements Result {}

        /** The provider gave no email: the visitor has to give one ({@link RequestExternalLinkConfirmation}). */
        record EmailRequired() implements Result {}

        /** The customer of that email already has another account of that provider. */
        record ProviderAlreadyLinked() implements Result {}

        /**
         * Nothing created or linked: the administrator's email (R-14), an email that cannot be one, or two sign-ins
         * racing for one account. The visitor cannot tell which.
         */
        record Refused() implements Result {}
    }
}
