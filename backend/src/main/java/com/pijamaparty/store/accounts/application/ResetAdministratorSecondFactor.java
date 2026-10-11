package com.pijamaparty.store.accounts.application;

/**
 * UC-04 (T-E1-16): the administrator lost both the phone and the recovery codes. Run at startup as {@code SYSTEM}
 * when the deployment asks for it; deletes the second factor and the recovery codes, so the next sign-in enrolls.
 */
public interface ResetAdministratorSecondFactor {

    Result execute();

    sealed interface Result {

        record Reset() implements Result {}

        /** Nothing to reset: no administrator, or it has no second factor. */
        record NothingToReset() implements Result {}
    }
}
