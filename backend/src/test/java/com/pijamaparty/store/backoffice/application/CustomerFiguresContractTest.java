package com.pijamaparty.store.backoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.api.CustomerCounts.Counts;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link CustomerFigures} (UC-33, T-E8-14): every registered customer counts, and those registered at the
 * given instant or later count as new; the administrator never counts.
 */
public abstract class CustomerFiguresContractTest<T extends CustomerFigures> extends ContractTest<T> {

    protected static final Instant MONTH_START = Instant.parse("2026-10-01T03:00:00Z");

    protected abstract void givenCustomer(Instant registeredAt);

    protected abstract void givenAdministrator(Instant registeredAt);

    @Test
    void givenCustomersBeforeAndSinceTheStartAndTheAdministrator_whenCounted_thenAllCustomersAndTheNewOnesCount() {
        givenCustomer(MONTH_START.minusSeconds(1));
        givenCustomer(MONTH_START);
        givenCustomer(MONTH_START.plusSeconds(86_400));
        givenAdministrator(MONTH_START.plusSeconds(3600));
        persistAndForget();

        assertThat(subject.count(MONTH_START)).isEqualTo(new Counts(3, 2));
    }

    @Test
    void givenNoCustomers_whenCounted_thenBothAreZero() {
        assertThat(subject.count(MONTH_START)).isEqualTo(new Counts(0, 0));
    }
}
