package com.pijamaparty.store.ordering.api;

import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import java.util.UUID;

/**
 * UC-17 (T-E3-09): a new tracking link for an existing order, for an email to carry. The order keeps only token
 * hashes (RNF-29.6), so every link is minted when it is sent; all of them, and the checkout's, keep working. Runs in
 * its own transaction, as the system.
 */
public interface TrackingLinks {

    /**
     * @return the new token, which only its holder sees: put it in the link and forget it
     * @throws IllegalArgumentException when there is no such order
     */
    TrackingToken issue(UUID orderId);
}
