package com.pijamaparty.store.accounts.application;

import java.util.function.Supplier;

/**
 * Outbound port for a change of the signed-in customer's own account that first runs slow work holding no database
 * connection, such as hashing a password (RNF-21): the implementation runs the block in its own short transaction, as
 * the current actor; ports of the caller's transaction join it.
 */
public interface CustomerAccountChanges {

    <R> R changing(Supplier<R> change);
}
