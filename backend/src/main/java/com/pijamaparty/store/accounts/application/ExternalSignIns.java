package com.pijamaparty.store.accounts.application;

import java.util.function.Supplier;

/**
 * Outbound port for the return from a provider (UC-27). Nobody is signed in, so the implementation runs the block in
 * its own short transaction as {@code SYSTEM}; ports of the caller's transaction join it, and what the block publishes
 * commits or rolls back with it. The database lets the system link external accounts only to customers (V34).
 */
public interface ExternalSignIns {

    <R> R signingIn(Supplier<R> signIn);
}
