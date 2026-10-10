package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.platform.domain.Actor;

/** UC-04: the signed-in administrator ({@code GET /api/admin/auth/me}). */
public interface GetCurrentAdministrator {

    Result execute(Actor actor);

    sealed interface Result {

        record Found(AdministratorView administrator) implements Result {}

        /** The actor is not an administrator, or its user no longer exists. */
        record NotSignedIn() implements Result {}
    }
}
