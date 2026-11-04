package com.pijamaparty.store.backoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link CustomerOrderTotals} (UC-34, T-E8-10): every order linked to a customer counts, whatever its
 * status; a guest's order, even with the customer's email, never does; a customer without orders has 0.
 */
public abstract class CustomerOrderTotalsContractTest<T extends CustomerOrderTotals> extends ContractTest<T> {

    /** An order in {@code status} linked to {@code customer}, or a guest's when empty, with Ana's email. */
    protected abstract void givenOrder(Optional<UUID> customer, String status);

    @Test
    void givenOrdersInSeveralStatusesAndAGuestsOne_whenCounted_thenEveryLinkedOrderCountsAndNoGuestOne() {
        UUID ana = UUID.randomUUID();
        UUID juan = UUID.randomUUID();
        UUID lia = UUID.randomUUID();
        givenOrder(Optional.of(ana), "DELIVERED");
        givenOrder(Optional.of(ana), "CANCELLED");
        givenOrder(Optional.of(ana), "AWAITING_PAYMENT");
        givenOrder(Optional.of(juan), "PAID");
        givenOrder(Optional.empty(), "PAID");
        persistAndForget();

        assertThat(subject.countsOf(List.of(ana, juan, lia))).isEqualTo(Map.of(ana, 3L, juan, 1L, lia, 0L));
        assertThat(subject.countsOf(List.of(juan))).isEqualTo(Map.of(juan, 1L));
        assertThat(subject.countsOf(List.of())).isEmpty();
    }
}
