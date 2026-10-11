package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.RecoveryCodeHash;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.List;

/**
 * Outbound port: the administrator's recovery codes, kept only as hashes (T-E1-16). Runs in the caller's transaction,
 * which is the sign-in's {@code SYSTEM} one: only {@code SYSTEM} may read or write them (V11).
 */
public interface RecoveryCodes {

    /** Deletes the user's codes and stores these instead. */
    void replace(UserId user, List<RecoveryCodeHash> codes);

    /** Marks an unused code of the user as used; false when it is not one of them or was used already. */
    boolean consume(UserId user, RecoveryCodeHash code, Instant now);

    /** How many unused codes the user has. */
    int unused(UserId user);

    void deleteAll(UserId user);
}
