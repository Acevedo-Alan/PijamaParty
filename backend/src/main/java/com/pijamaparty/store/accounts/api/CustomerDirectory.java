package com.pijamaparty.store.accounts.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-34 (T-E8-10): the registered customers for the back office's "Clientes", newest first, 50 per page, filtered by
 * a search (name or email) and by the store dates they registered on. Joins the caller's transaction, so its security
 * context decides which users are visible (RNF-27): the administrator sees the customers (V44), never itself. Personal
 * data: never logged.
 */
public interface CustomerDirectory {

    int PAGE_SIZE = 50;

    /**
     * {@code page} counts from 0.
     *
     * @param search as typed: 2 to 100 characters of the name or email, ignoring case and accents, every character
     *     literal; blank is no search
     * @param from a store date, inclusive
     * @param to a store date, inclusive
     */
    Listing list(Optional<String> search, Optional<LocalDate> from, Optional<LocalDate> to, int page);

    sealed interface Listing {

        record Listed(List<Customer> customers, boolean hasMore) implements Listing {

            public Listed {
                customers = List.copyOf(customers);
            }
        }

        /** A search of 1 character or over 100; nothing is listed. */
        record InvalidSearch() implements Listing {}

        /** {@code from} after {@code to}; nothing is listed. */
        record InvalidPeriod() implements Listing {}
    }

    /** A registered customer. Personal data: never logged. */
    record Customer(UUID id, String name, String email, Instant registeredAt) {

        public Customer {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(email, "email");
            Objects.requireNonNull(registeredAt, "registeredAt");
        }

        @Override
        public String toString() {
            return "Customer[id=" + id + ", name=<hidden>, email=<hidden>, registeredAt=" + registeredAt + "]";
        }
    }
}
