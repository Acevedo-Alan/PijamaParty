package com.pijamaparty.store.backoffice.adapter.out.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.backoffice.application.LowStockThresholdContractTest;
import com.pijamaparty.store.platform.testing.ApiTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Through the settings module's public API to PostgreSQL (UC-36, D-E8-03). */
@ApiTest
class SettingsLowStockThresholdContractTest extends LowStockThresholdContractTest<SettingsLowStockThreshold> {

    private final SettingsLowStockThreshold adapter;
    private final JdbcTemplate owner;

    SettingsLowStockThresholdContractTest(SettingsLowStockThreshold adapter, PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected SettingsLowStockThreshold createSubject() {
        return adapter;
    }

    @AfterEach
    void backToTheDefault() {
        owner.update("update store_settings set low_stock_threshold = 3");
    }

    @Test
    void givenANewStore_whenTheThresholdIsRead_thenItIsTheDefaultOf3Units() {
        assertThat(subject.units()).isEqualTo(3);
    }

    @Test
    void givenAThresholdSavedInTheStoreSettings_whenRead_thenItIsThatOne() {
        owner.update("update store_settings set low_stock_threshold = 0");

        assertThat(subject.units()).isZero();
    }
}
