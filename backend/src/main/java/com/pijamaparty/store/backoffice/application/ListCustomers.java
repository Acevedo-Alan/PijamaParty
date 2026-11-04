package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.platform.domain.Actor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-34 (T-E8-10): "Clientes" lists the registered customers, newest first, 50 per page, each with its number of
 * orders, filtered by a search (name or email) and by the store dates they registered on, each optional and combined.
 */
public interface ListCustomers {

    Result execute(Query query);

    /**
     * {@code page} counts from 0.
     *
     * @param search as typed; personal data, never logged
     * @param from a store date, inclusive
     * @param to a store date, inclusive
     */
    record Query(Actor actor, Optional<String> search, Optional<LocalDate> from, Optional<LocalDate> to, int page) {

        public Query {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(search, "search");
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            if (page < 0) {
                throw new IllegalArgumentException("page must not be negative");
            }
        }

        @Override
        public String toString() {
            return "Query[actor=" + actor + ", search=<hidden>, from=" + from + ", to=" + to + ", page=" + page + "]";
        }
    }

    /** A row of the list. Personal data: never logged. */
    record CustomerRow(UUID id, String name, String email, Instant registeredAt, long orders) {

        @Override
        public String toString() {
            return "CustomerRow[id=" + id + ", name=<hidden>, email=<hidden>, registeredAt=" + registeredAt
                + ", orders=" + orders + "]";
        }
    }

    sealed interface Result {

        record Listed(List<CustomerRow> customers, boolean hasMore) implements Result {

            public Listed {
                customers = List.copyOf(customers);
            }
        }

        /** A search of 1 character or over 100; nothing is listed. */
        record InvalidSearch() implements Result {}

        /** {@code from} after {@code to}; nothing is listed. */
        record InvalidPeriod() implements Result {}

        record Forbidden() implements Result {}
    }
}
