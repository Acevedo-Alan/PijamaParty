package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link OrderCountsByCustomer} (UC-34, T-E8-10): every order linked to a customer counts, whatever its
 * status; a guest's order never does; only the customers with orders come back, one entry each.
 */
public abstract class OrderCountsByCustomerContractTest<T extends OrderCountsByCustomer> extends ContractTest<T> {

    /** An order in {@code status} linked to {@code customer}, or a guest's when empty. */
    protected abstract void givenOrder(Optional<UUID> customer, String status);

    @Test
    void givenOrdersInSeveralStatusesAndAGuestsOne_whenCounted_thenEveryLinkedOneCountsAndOnlyCustomersWithOrdersCome() {
        UUID ana = UUID.randomUUID();
        UUID juan = UUID.randomUUID();
        UUID lia = UUID.randomUUID();
        givenOrder(Optional.of(ana), "DELIVERED");
        givenOrder(Optional.of(ana), "CANCELLED");
        givenOrder(Optional.of(ana), "AWAITING_PAYMENT");
        givenOrder(Optional.of(juan), "PAID");
        givenOrder(Optional.empty(), "PAID");
        persistAndForget();

        assertThat(subject.countsOf(List.of(ana, juan, lia, ana))).isEqualTo(Map.of(ana, 3L, juan, 1L));
        assertThat(subject.countsOf(List.of(lia))).isEmpty();
    }
}
