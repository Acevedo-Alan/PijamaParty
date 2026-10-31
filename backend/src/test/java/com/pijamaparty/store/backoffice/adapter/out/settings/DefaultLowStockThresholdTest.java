package com.pijamaparty.store.backoffice.adapter.out.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.backoffice.application.LowStockThresholdContractTest;
import org.junit.jupiter.api.Test;

class DefaultLowStockThresholdTest extends LowStockThresholdContractTest<DefaultLowStockThreshold> {

    @Override
    protected DefaultLowStockThreshold createSubject() {
        return new DefaultLowStockThreshold();
    }

    @Test
    void givenNoStoreSettingsYet_whenTheThresholdIsRead_thenItIsTheDefaultOf3Units() {
        assertThat(subject.units()).isEqualTo(3);
    }
}
