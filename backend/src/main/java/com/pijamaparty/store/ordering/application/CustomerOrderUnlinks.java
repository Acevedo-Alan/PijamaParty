package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;

/**
 * Outbound write port (UC-28): unlinks a deleted customer's orders, within the caller's transaction, which runs as that
 * customer once its user is gone (V36).
 */
public interface CustomerOrderUnlinks {

    /**
     * Sets every order of {@code customer} without a user, unlinked {@code at}, whatever its status.
     *
     * @return how many orders were unlinked; 0 when there were none or they were already unlinked
     */
    int unlinkAll(UserId customer, Instant at);
}
