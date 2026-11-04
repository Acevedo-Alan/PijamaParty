package com.pijamaparty.store.backoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.api.CustomerDirectory.Customer;
import com.pijamaparty.store.accounts.api.CustomerDirectory.Listing;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link RegisteredCustomers} (UC-34, T-E8-10): the customers newest first; a search of 1 character or
 * over 100, or a period whose start is after its end, lists nothing; the dates are the store's
 * (America/Argentina/Buenos_Aires, UTC−3), both inclusive.
 */
public abstract class RegisteredCustomersContractTest<T extends RegisteredCustomers> extends ContractTest<T> {

    /** A customer registered at {@code registeredAt}; answers its id. */
    protected abstract UUID givenCustomer(String name, String email, Instant registeredAt);

    @Test
    void givenCustomers_whenListed_thenTheyComeNewestFirst() {
        UUID ana = givenCustomer("Ana Gómez", "ana@example.com", Instant.parse("2026-10-10T12:00:00Z"));
        UUID juan = givenCustomer("Juan Pérez", "juan@example.com", Instant.parse("2026-10-11T12:00:00Z"));
        persistAndForget();

        assertThat(subject.list(Optional.empty(), Optional.empty(), Optional.empty(), 0))
            .isInstanceOfSatisfying(Listing.Listed.class, listed -> {
                assertThat(listed.customers()).extracting(Customer::id).containsExactly(juan, ana);
                assertThat(listed.hasMore()).isFalse();
            });
        assertThat(subject.list(Optional.of("gomez"), Optional.empty(), Optional.empty(), 0))
            .isInstanceOfSatisfying(Listing.Listed.class, listed ->
                assertThat(listed.customers()).extracting(Customer::id).containsExactly(ana));
    }

    @Test
    void givenARegistrationPeriod_whenListed_thenTheDaysAreTheStoresBothInclusive() {
        givenCustomer("Antes", "antes@example.com", Instant.parse("2026-10-01T02:59:00Z"));
        givenCustomer("Primer día", "primero@example.com", Instant.parse("2026-10-01T03:00:00Z"));
        givenCustomer("Último día", "ultimo@example.com", Instant.parse("2026-10-16T02:59:00Z"));
        givenCustomer("Después", "despues@example.com", Instant.parse("2026-10-16T03:00:00Z"));
        persistAndForget();

        assertThat(subject.list(Optional.empty(), Optional.of(LocalDate.parse("2026-10-01")),
            Optional.of(LocalDate.parse("2026-10-15")), 0))
            .isInstanceOfSatisfying(Listing.Listed.class, listed ->
                assertThat(listed.customers()).extracting(Customer::name).containsExactly("Último día", "Primer día"));
    }

    @Test
    void givenAnInvalidSearchOrPeriod_whenListed_thenNothingIsListed() {
        givenCustomer("Ana Gómez", "ana@example.com", Instant.parse("2026-10-10T12:00:00Z"));
        persistAndForget();

        assertThat(subject.list(Optional.of("a"), Optional.empty(), Optional.empty(), 0))
            .isEqualTo(new Listing.InvalidSearch());
        assertThat(subject.list(Optional.of("a".repeat(101)), Optional.empty(), Optional.empty(), 0))
            .isEqualTo(new Listing.InvalidSearch());
        assertThat(subject.list(Optional.empty(), Optional.of(LocalDate.parse("2026-10-11")),
            Optional.of(LocalDate.parse("2026-10-10")), 0)).isEqualTo(new Listing.InvalidPeriod());
        assertThat(subject.list(Optional.empty(), Optional.empty(), Optional.of(LocalDate.MAX), 0))
            .isEqualTo(new Listing.InvalidPeriod());
        assertThat(subject.list(Optional.empty(), Optional.of(LocalDate.parse("1999-12-31")), Optional.empty(), 0))
            .isEqualTo(new Listing.InvalidPeriod());
    }
}
