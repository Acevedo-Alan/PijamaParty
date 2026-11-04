package com.pijamaparty.store.backoffice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.accounts.api.CustomerDirectory;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** UC-34 (T-E8-10): "Clientes", the customers with their number of orders. */
class ListCustomersServiceTest {

    private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));
    private static final Instant T0 = Instant.parse("2026-10-10T12:00:00Z");

    private final InMemoryRegisteredCustomers customers = new InMemoryRegisteredCustomers();
    private final InMemoryCustomerOrderTotals orders = new InMemoryCustomerOrderTotals();
    private final ListCustomers list = new ListCustomersService(customers, orders);

    @Test
    void givenCustomersWithOrders_whenListed_thenEachRowHasItsNumberOfOrdersNewestFirst() {
        UUID ana = customers.add("Ana Gómez", "ana@example.com", T0);
        UUID juan = customers.add("Juan Pérez", "juan@example.com", T0.plusSeconds(60));
        orders.order(Optional.of(ana));
        orders.order(Optional.of(ana));
        orders.order(Optional.empty());

        var listed = (ListCustomers.Result.Listed) list.execute(query(Optional.empty(), 0));

        assertThat(listed.customers()).containsExactly(
            new ListCustomers.CustomerRow(juan, "Juan Pérez", "juan@example.com", T0.plusSeconds(60), 0),
            new ListCustomers.CustomerRow(ana, "Ana Gómez", "ana@example.com", T0, 2));
        assertThat(listed.hasMore()).isFalse();
    }

    @Test
    void givenMoreThanAPage_whenListed_thenPagesOf50ComeWithWhetherMoreFollow() {
        IntStream.rangeClosed(1, CustomerDirectory.PAGE_SIZE + 1)
            .forEach(i -> customers.add("Cliente " + i, "c" + i + "@example.com", T0.plusSeconds(i)));

        var first = (ListCustomers.Result.Listed) list.execute(query(Optional.empty(), 0));
        var second = (ListCustomers.Result.Listed) list.execute(query(Optional.empty(), 1));

        assertThat(first.customers()).hasSize(CustomerDirectory.PAGE_SIZE);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.customers()).hasSize(1);
    }

    @Test
    void givenAnInvalidSearchOrPeriod_whenListed_thenTheyAreRefusedAndASearchIsNeverInTheQueryText() {
        assertThat(list.execute(query(Optional.of("a"), 0))).isEqualTo(new ListCustomers.Result.InvalidSearch());
        assertThat(list.execute(new ListCustomers.Query(ADMIN, Optional.empty(),
            Optional.of(LocalDate.parse("2026-10-11")), Optional.of(LocalDate.parse("2026-10-10")), 0)))
            .isEqualTo(new ListCustomers.Result.InvalidPeriod());
        assertThat(query(Optional.of("ana@example.com"), 0).toString()).doesNotContain("ana");
        assertThat(new ListCustomers.CustomerRow(UUID.randomUUID(), "Ana", "ana@example.com", T0, 1).toString())
            .doesNotContain("Ana", "ana@");
    }

    @Test
    void givenSomeoneOtherThanTheAdministrator_whenListing_thenItIsForbidden() {
        Actor customer = Actor.customer(UserId.of(UUID.randomUUID()), Optional.empty(), Optional.empty());

        assertThat(list.execute(new ListCustomers.Query(customer, Optional.empty(), Optional.empty(), Optional.empty(),
            0))).isEqualTo(new ListCustomers.Result.Forbidden());
        assertThatThrownBy(() -> query(Optional.empty(), -1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static ListCustomers.Query query(Optional<String> search, int page) {
        return new ListCustomers.Query(ADMIN, search, Optional.empty(), Optional.empty(), page);
    }
}
