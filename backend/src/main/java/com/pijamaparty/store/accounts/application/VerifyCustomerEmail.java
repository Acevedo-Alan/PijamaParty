package com.pijamaparty.store.accounts.application;

/** UC-19: the link of the verification email is opened; the customer can sign in afterwards. */
public interface VerifyCustomerEmail {

    Result execute(Command command);

    /** The token of the link, as received: a secret, never logged. */
    record Command(String token) {

        @Override
        public String toString() {
            return "Command[***]";
        }
    }

    sealed interface Result {

        record Verified() implements Result {}

        /** Unknown, used, expired or meant for something else: the caller cannot tell which. */
        record InvalidLink() implements Result {}
    }
}
