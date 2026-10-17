package com.pijamaparty.store.accounts.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link CustomerDataContributor} (UC-28, T-E4-16): a module's part of the customer's data download. It
 * always answers the same section, never a null list, and only the given customer's records. {@code ordering}
 * implements it now and {@code reviews} in E5.
 */
public abstract class CustomerDataContributorContractTest<T extends CustomerDataContributor>
    extends ContractTest<T> {

    /** The section this implementation contributes. */
    protected abstract CustomerDataSection expectedSection();

    /** A customer with {@code records} records of the section, stored as its module stores them. */
    protected abstract UserId customerWith(int records);

    /** Runs the export as the download does: the real adapter joins a read-only transaction as {@code customer}. */
    protected List<?> exportAs(UserId customer, Supplier<List<?>> export) {
        return export.get();
    }

    @Test
    void givenTheContributor_whenAskedTwice_thenItAnswersTheSameSection() {
        assertThat(subject.section()).isEqualTo(expectedSection());
        assertThat(subject.section()).isEqualTo(expectedSection());
    }

    @Test
    void givenACustomerWithoutRecords_whenExporting_thenItGetsAnEmptyListNeverNull() {
        UserId ana = customerWith(0);
        customerWith(2);
        persistAndForget();

        assertThat(exportAs(ana, () -> subject.export(ana))).isNotNull().isEmpty();
    }

    @Test
    void givenTwoCustomers_whenEachExports_thenEachGetsOnlyItsOwnRecords() {
        UserId ana = customerWith(2);
        UserId bruno = customerWith(1);
        persistAndForget();

        assertThat(exportAs(ana, () -> subject.export(ana))).hasSize(2);
        assertThat(exportAs(bruno, () -> subject.export(bruno))).hasSize(1);
    }
}
