package com.pijamaparty.store.shipping.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** T-E3-01: the shipping schema (V16) enforces the UC-12 rules, connected as the application role. */
@ApiTest
class ShippingSchemaTest {

    private static final String INSERT_RATE = """
        insert into shipping_zone_rate (province, cost_cents, updated_at) values (?, ?, now())
        """;

    private final JdbcTemplate app;
    private final JdbcTemplate owner;

    ShippingSchemaTest(JdbcTemplate jdbc, PostgreSQLContainer<?> postgres) {
        this.app = jdbc;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate shipping_zone_rate");
        owner.update("update shop_setting set free_shipping_threshold_cents = null");
    }

    @Test
    void givenAProvince_whenItsCostIsStored_thenAFreeOrPaidCostIsAccepted() {
        app.update(INSERT_RATE, "SANTA_FE", 0);
        app.update(INSERT_RATE, "CIUDAD_DE_BUENOS_AIRES", 650_000);

        assertThat(app.queryForObject("select count(*) from shipping_zone_rate", Integer.class)).isEqualTo(2);
    }

    @Test
    void givenAnUnknownProvinceOrACostOutOfRange_whenStored_thenItIsRejected() {
        assertThatThrownBy(() -> app.update(INSERT_RATE, "Santa Fe", 100))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("shipping_zone_rate_province_ck");
        assertThatThrownBy(() -> app.update(INSERT_RATE, "SANTA_FE", -1))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("shipping_zone_rate_cost_ck");
        assertThatThrownBy(() -> app.update(INSERT_RATE, "SANTA_FE", 1_000_000_001))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("shipping_zone_rate_cost_ck");
    }

    @Test
    void givenTheShopSettings_whenRead_thenThereIsASingleRowWithoutFreeShipping() {
        assertThat(app.queryForList("select free_shipping_threshold_cents from shop_setting", Long.class))
            .containsExactly((Long) null);
    }

    @Test
    void givenTheShopSettings_whenTheThresholdIsUpdated_thenOnlyAPositiveAmountIsAccepted() {
        assertThat(app.update("update shop_setting set free_shipping_threshold_cents = 5000000")).isOne();

        assertThatThrownBy(() -> app.update("update shop_setting set free_shipping_threshold_cents = 0"))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("shop_setting_free_shipping_threshold_ck");
    }

    @Test
    void givenTheShopSettings_whenTheApplicationTriesToAddOrDeleteARow_thenItIsDenied() {
        assertThatThrownBy(() -> app.update("insert into shop_setting (id, updated_at) values (false, now())"))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
        assertThatThrownBy(() -> app.update("delete from shop_setting"))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
    }
}
