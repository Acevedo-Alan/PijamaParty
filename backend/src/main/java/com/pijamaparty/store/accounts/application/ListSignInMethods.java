package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.platform.domain.Actor;

/** UC-27 (T-E4-15): how the signed-in customer signs in ({@code GET /api/account/identities}). */
public interface ListSignInMethods {

    Result execute(Actor actor);

    sealed interface Result {

        record Listed(SignInMethodsView methods) implements Result {}

        /** The actor is not a customer, or its account no longer exists. */
        record NotSignedIn() implements Result {}
    }
}
