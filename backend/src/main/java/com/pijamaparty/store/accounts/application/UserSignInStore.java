package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import java.util.Optional;
import java.util.function.Function;

/**
 * Outbound port for sign-in. Nobody is signed in yet, so the implementation runs its own short transactions as
 * {@code SYSTEM} (RNF-27; use cases never switch to {@code SYSTEM} themselves, ARCHITECTURE.md section 9). The
 * password check happens between the two calls, outside any transaction, so a slow hash never holds a database
 * connection or a row lock (RNF-21).
 */
public interface UserSignInStore {

    /** Reads the user in a short transaction, without locking it. */
    Optional<User> find(Email email);

    /**
     * Loads the user with a row lock, so concurrent attempts on one account are counted one after the other, hands
     * it to {@code record}, and saves it. The database only lets {@code SYSTEM} change the attempt counter and the
     * lock (V5).
     */
    <R> R withUserLockedForSignIn(Email email, Function<Optional<User>, R> record);
}
