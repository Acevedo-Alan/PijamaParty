package com.pijamaparty.store.inventory.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * T-E1-07, T-E3-01: the view {@code v_variant_availability} (V9, V17) that the catalog reads, connected as the
 * application role.
 */
@ApiTest
class VariantAvailabilityViewTest {

    private final JdbcTemplate app;
    private final JdbcTemplate owner;

    VariantAvailabilityViewTest(JdbcTemplate jdbc, PostgreSQLContainer<?> postgres) {
        this.app = jdbc;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate stock_movement, stock_level");
    }

    @Test
    void givenAStockLevel_whenReadThroughTheView_thenTheAvailableStockIsTheStockOnHand() {
        UUID variant = UUID.randomUUID();
        app.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, 7, now())", variant);

        assertThat(app.queryForObject("select available from v_variant_availability where variant_id = ?",
            Integer.class, variant)).isEqualTo(7);
    }

    @Test
    void givenReservedUnits_whenReadThroughTheView_thenTheAvailableStockIsTheStockOnHandMinusTheReserved() {
        UUID variant = UUID.randomUUID();
        app.update("insert into stock_level (variant_id, on_hand, reserved, updated_at) values (?, 7, 3, now())",
            variant);

        assertThat(app.queryForObject("select available from v_variant_availability where variant_id = ?",
            Integer.class, variant)).isEqualTo(4);
    }

    @Test
    void givenTheApplicationRole_whenWritingThroughTheView_thenItIsRefused() {
        UUID variant = UUID.randomUUID();
        app.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, 7, now())", variant);

        // available is computed since V17, so only variant_id could still be written through the view.
        assertThatThrownBy(() -> app.update(
                "update v_variant_availability set variant_id = gen_random_uuid() where variant_id = ?", variant))
            .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> app.update("delete from v_variant_availability where variant_id = ?", variant))
            .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
    }
}
