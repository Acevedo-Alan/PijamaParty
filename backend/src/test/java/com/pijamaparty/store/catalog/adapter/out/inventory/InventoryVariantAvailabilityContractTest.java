package com.pijamaparty.store.catalog.adapter.out.inventory;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.catalog.application.VariantAvailabilityLookupContractTest;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.testing.ApiTest;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Through the inventory module's public API to the view {@code v_variant_availability} in PostgreSQL. */
@ApiTest
class InventoryVariantAvailabilityContractTest
    extends VariantAvailabilityLookupContractTest<InventoryVariantAvailability> {

    private final InventoryVariantAvailability adapter;
    private final JdbcTemplate owner;

    InventoryVariantAvailabilityContractTest(InventoryVariantAvailability adapter, PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected InventoryVariantAvailability createSubject() {
        return adapter;
    }

    @Override
    protected void givenAvailable(VariantId variant, int units) {
        owner.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, ?, now())",
            variant.value(), units);
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate stock_movement, stock_level");
    }
}
