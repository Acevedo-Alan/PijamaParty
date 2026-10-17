package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;

/** Outbound write port (UC-22, T-E4-08): links guest orders to a customer, within the caller's transaction. */
public interface GuestOrderLinks {

    /**
     * Sets {@code customer} on every order without a user and never unlinked (UC-28) whose buyer email, as last
     * corrected (UC-17), is {@code email}.
     *
     * @param email already normalized; personal data, never logged (R-05)
     * @return how many orders were linked
     */
    int link(UserId customer, String email, Instant at);
}
