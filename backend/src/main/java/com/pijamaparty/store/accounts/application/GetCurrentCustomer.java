package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.platform.domain.Actor;

/** UC-19: the signed-in customer ({@code GET /api/account/me}), for the storefront header. */
public interface GetCurrentCustomer {

    Result execute(Actor actor);

    sealed interface Result {

        record Found(CustomerView customer) implements Result {}

        /** The actor is not a customer, or its user no longer exists. */
        record NotSignedIn() implements Result {}
    }
}
