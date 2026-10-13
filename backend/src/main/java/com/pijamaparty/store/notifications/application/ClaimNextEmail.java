package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.OutboxEmail;
import java.util.Optional;

/**
 * RNF-23: takes the next email due, in its own short transaction, so no connection is held while it is sent
 * (RNF-21). A step of {@link SendNextEmail}, behind an interface so its transaction applies.
 */
public interface ClaimNextEmail {

    /**
     * The oldest pending email due that no one else is taking, {@linkplain OutboxEmail#claim claimed} and stored so:
     * an {@code Attempt} counts the attempt and waits until its retry instant, an {@code Exhausted} one becomes
     * {@code FAILED}. Empty when none is due.
     */
    Optional<OutboxEmail.Claim> execute();
}
