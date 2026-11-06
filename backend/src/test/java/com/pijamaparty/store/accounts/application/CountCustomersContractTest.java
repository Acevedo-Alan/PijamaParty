package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.api.CustomerCounts.Counts;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link CountCustomers} (UC-33, T-E8-14): the users with role {@code CUSTOMER}, all and those created at
 * the given instant or later; never the administrator.
 */
public abstract class CountCustomersContractTest<T extends CountCustomers> extends ContractTest<T> {

    protected static final Instant SINCE = Instant.parse("2026-10-01T03:00:00Z");

    /** Stores a user as it is; {@code role} is {@code CUSTOMER} or {@code ADMIN}. */
    protected abstract void givenUser(String role, Instant registeredAt);

    @Test
    void givenCustomersAroundTheInstantAndTheAdministrator_whenCounted_thenOnlyCustomersCount() {
        givenUser("CUSTOMER", SINCE.minusSeconds(1));
        givenUser("CUSTOMER", SINCE);
        givenUser("ADMIN", SINCE.plusSeconds(60));
        persistAndForget();

        assertThat(subject.count(SINCE)).isEqualTo(new Counts(2, 1));
    }
}
