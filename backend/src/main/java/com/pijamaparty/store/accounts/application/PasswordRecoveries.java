package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Outbound port for a password recovery request (UC-19). Nobody is signed in, so the implementation runs its own
 * short transaction as {@code SYSTEM}, which may only read the user here.
 */
public interface PasswordRecoveries {

    /**
     * Finds the user of {@code email}, if any, and hands it to {@code request} in the same transaction, so what it
     * publishes commits with it.
     */
    void requesting(Email email, Consumer<Optional<User>> request);
}
