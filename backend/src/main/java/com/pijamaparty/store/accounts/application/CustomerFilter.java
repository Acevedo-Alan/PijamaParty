package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.CustomerSearch;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-34 (T-E8-10): which customers the back-office list shows; every part optional, all of them combined.
 *
 * @param registeredFrom the first instant inside, inclusive
 * @param registeredBefore the first instant outside, exclusive
 */
public record CustomerFilter(Optional<CustomerSearch> search, Optional<Instant> registeredFrom,
                             Optional<Instant> registeredBefore) {

    public static final CustomerFilter ALL = new CustomerFilter(Optional.empty(), Optional.empty(), Optional.empty());

    public CustomerFilter {
        Objects.requireNonNull(search, "search");
        Objects.requireNonNull(registeredFrom, "registeredFrom");
        Objects.requireNonNull(registeredBefore, "registeredBefore");
    }
}
