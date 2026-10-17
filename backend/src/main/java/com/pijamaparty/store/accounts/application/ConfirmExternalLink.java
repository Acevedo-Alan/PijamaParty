package com.pijamaparty.store.accounts.application;

/** UC-27 (T-E4-14): the link of the link confirmation email is opened; the customer signs in with the provider next. */
public interface ConfirmExternalLink {

    Result execute(Command command);

    /** The token of the link, as received: a secret, never logged. */
    record Command(String token) {

        @Override
        public String toString() {
            return "Command[***]";
        }
    }

    sealed interface Result {

        /** Linked now, or already linked to this customer by an earlier confirmation. */
        record Linked() implements Result {}

        /** Unknown, used, expired or meant for something else: the caller cannot tell which. */
        record InvalidLink() implements Result {}

        /**
         * The external account is linked to someone else, or the customer already has another account of that
         * provider. The token is used up: this link can never succeed.
         */
        record AlreadyLinked() implements Result {}
    }
}
