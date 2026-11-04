package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.api.CustomerDirectory.Customer;
import com.pijamaparty.store.accounts.domain.CustomerSearch;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link CustomerPages} (UC-34, T-E8-10): the users with role {@code CUSTOMER}, newest first, never the
 * administrator; the search is contained in the name or the email, ignoring case and accents, every character literal.
 */
public abstract class CustomerPagesContractTest<T extends CustomerPages> extends ContractTest<T> {

    /** Whole seconds: PostgreSQL keeps microseconds, so the instants survive the round trip. */
    protected static final Instant T0 = Instant.parse("2026-10-10T12:00:00Z");

    /** Stores a user as it is; {@code role} is {@code CUSTOMER} or {@code ADMIN}. */
    protected abstract void givenUser(UUID id, String name, String email, String role, Instant registeredAt);

    private UUID customer(String name, String email, int minutes) {
        UUID id = UUID.randomUUID();
        givenUser(id, name, email, "CUSTOMER", T0.plusSeconds(60L * minutes));
        return id;
    }

    @Test
    void givenThreeCustomersAndTheAdministrator_whenListed_thenTheCustomersComeNewestFirstPageByPage() {
        UUID oldest = customer("Ana Gómez", "ana@example.com", 0);
        UUID middle = customer("Juan Pérez", "juan@example.com", 1);
        UUID newest = customer("Lía Sosa", "lia@example.com", 2);
        givenUser(UUID.randomUUID(), "Dueña", "owner@example.com", "ADMIN", T0.plusSeconds(600));
        persistAndForget();

        assertThat(subject.page(CustomerFilter.ALL, 0, 2)).containsExactly(
            new Customer(newest, "Lía Sosa", "lia@example.com", T0.plusSeconds(120)),
            new Customer(middle, "Juan Pérez", "juan@example.com", T0.plusSeconds(60)));
        assertThat(subject.page(CustomerFilter.ALL, 2, 2)).extracting(Customer::id).containsExactly(oldest);
        assertThat(subject.page(CustomerFilter.ALL, 3, 2)).isEmpty();
    }

    @Test
    void givenASearch_whenListed_thenTheNameOrEmailContainsItIgnoringCaseAndAccents() {
        customer("Ana Gómez", "ana@example.com", 0);
        customer("Juan Pérez", "juan@example.com", 1);
        persistAndForget();

        assertThat(names(search("gómez"))).containsExactly("Ana Gómez");
        assertThat(names(search("GÓMEZ"))).containsExactly("Ana Gómez");
        assertThat(names(search("gomez"))).containsExactly("Ana Gómez");
        assertThat(names(search("JUAN@EXAMPLE"))).containsExactly("Juan Pérez");
        assertThat(names(search("example.com"))).containsExactly("Juan Pérez", "Ana Gómez");
    }

    @Test
    void givenLikeWildcardsInTheSearch_whenListed_thenEveryCharacterIsLiteral() {
        customer("Ana Uno", "ana_1@x.com", 0);
        customer("Ana Once", "ana11@x.com", 1);
        customer("Descuento 100%", "promo@x.com", 2);
        customer("Cien 1000", "cien@x.com", 3);
        persistAndForget();

        assertThat(names(search("ana_1"))).containsExactly("Ana Uno");
        assertThat(names(search("00%"))).containsExactly("Descuento 100%");
    }

    @Test
    void givenARegistrationPeriod_whenListed_thenOnlyTheCustomersRegisteredInsideShow() {
        customer("Antes", "antes@example.com", 0);
        customer("Dentro", "dentro@example.com", 10);
        customer("Justo después", "despues@example.com", 20);
        persistAndForget();

        var filter = new CustomerFilter(Optional.empty(), Optional.of(T0.plusSeconds(60)),
            Optional.of(T0.plusSeconds(1200)));
        assertThat(names(filter)).containsExactly("Dentro");
        assertThat(names(new CustomerFilter(Optional.of(new CustomerSearch("dentro")), Optional.of(T0),
            Optional.empty()))).containsExactly("Dentro");
    }

    private static CustomerFilter search(String text) {
        return new CustomerFilter(Optional.of(new CustomerSearch(text)), Optional.empty(), Optional.empty());
    }

    private List<String> names(CustomerFilter filter) {
        return subject.page(filter, 0, 100).stream().map(Customer::name).toList();
    }
}
