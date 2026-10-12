package com.pijamaparty.store.ordering.adapter.out.shipping;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.application.ShippingQuotesContractTest;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.shipping.api.Province;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Through the public API of the shipping module to its tables in PostgreSQL. */
@ApiTest
class ShippingModuleQuotesContractTest extends ShippingQuotesContractTest<ShippingModuleQuotes> {

    private final ShippingModuleQuotes adapter;
    private final JdbcTemplate owner;

    ShippingModuleQuotesContractTest(ShippingModuleQuotes adapter, PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected ShippingModuleQuotes createSubject() {
        return adapter;
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate shipping_zone_rate");
        owner.update("update shop_setting set free_shipping_threshold_cents = null");
    }

    @Override
    protected void givenRate(Province province, long costCents) {
        owner.update("insert into shipping_zone_rate (province, cost_cents, updated_at) values (?, ?, now())",
            province.name(), costCents);
    }

    @Override
    protected void givenFreeShippingFrom(long thresholdCents) {
        owner.update("update shop_setting set free_shipping_threshold_cents = ?", thresholdCents);
    }
}
