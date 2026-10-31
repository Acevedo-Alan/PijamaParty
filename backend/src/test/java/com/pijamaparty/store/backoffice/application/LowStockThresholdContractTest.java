package com.pijamaparty.store.backoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ContractTest;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link LowStockThreshold} (UC-33, D-E8-03): a whole number of units, never negative and within what the
 * store's settings accept, so the summary's low-stock block always has a threshold to compare with.
 */
public abstract class LowStockThresholdContractTest<T extends LowStockThreshold> extends ContractTest<T> {

    /** The highest threshold the settings of UC-36 accept. */
    protected static final int MAX_UNITS = 1000;

    @Test
    void givenTheThreshold_whenRead_thenItIsBetween0AndTheMaximumAndStaysTheSame() {
        int units = subject.units();

        assertThat(units).isBetween(0, MAX_UNITS);
        assertThat(subject.units()).isEqualTo(units);
    }
}
