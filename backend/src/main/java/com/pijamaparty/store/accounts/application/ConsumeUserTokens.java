package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.UserToken;
import com.pijamaparty.store.platform.domain.AccessToken.AccountToken;
import java.util.Optional;
import java.util.function.Function;

/**
 * Outbound port for opening an email link (UC-19). Nobody is signed in, so the implementation runs its own
 * transaction as {@code SYSTEM} bound to the token's hash: the database then lets it verify the email or replace the
 * password only of that token's user, and only once the token is used in that transaction (V29).
 */
public interface ConsumeUserTokens {

    /**
     * Locks the token whose hash is that of {@code token}, if any, and hands it to {@code consume}, all in that
     * transaction. Ports of the caller's transaction ({@link LoadUser}, {@link SaveUser}) join it.
     */
    <R> R consuming(AccountToken token, Function<Optional<UserToken>, R> consume);

    /** Records that the token was used, at once, so the database sees it before the user changes. */
    void markUsed(UserToken token);
}
