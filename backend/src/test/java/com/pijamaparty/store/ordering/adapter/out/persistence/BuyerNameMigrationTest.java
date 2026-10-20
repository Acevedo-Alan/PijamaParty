package com.pijamaparty.store.ordering.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * T-E7-10 (D-13): V40 splits the orders placed before it at the first space, in a database of its own migrated up to
 * V39 first.
 */
@ApiTest
class BuyerNameMigrationTest {

    private static final String DATABASE = "buyer_name_migration";

    // As V14 left the table, before V40.
    private static final String INSERT_ORDER = """
        insert into orders (id, number, tracking_token_hash, status, customer_name, customer_email, customer_phone,
                            national_id, street, street_number, city, province, postal_code, subtotal_cents,
                            discount_cents, shipping_cents, total_cents, payment_provider, terms_version,
                            reservation_expires_at, created_at, updated_at)
        values (?, nextval('order_number_seq'), md5(random()::text) || md5(random()::text), 'PAID', ?,
                'ana@example.com', '3425551234', '12345678', 'San Martín', '1234', 'Santa Fe', 'SANTA_FE', '3000',
                2000000, 0, 300000, 2300000, 'MERCADOPAGO', '2026-10', now() + interval '30 minutes', now(), now())
        """;

    private final PostgreSQLContainer<?> postgres;
    private final JdbcTemplate superuser;

    BuyerNameMigrationTest(PostgreSQLContainer<?> postgres) {
        this.postgres = postgres;
        this.superuser = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
            postgres.getPassword()));
    }

    @BeforeEach
    void createDatabase() {
        superuser.execute("drop database if exists buyer_name_migration");
        superuser.execute("create database buyer_name_migration owner tienda_owner");
        new JdbcTemplate(new DriverManagerDataSource(urlOf(DATABASE), postgres.getUsername(), postgres.getPassword()))
            .execute("alter schema public owner to tienda_owner");
    }

    // Without the container's 10-second statement timeout: forcing the drop waits for the other sessions to end, and
    // the full suite keeps the server busy.
    @AfterEach
    void dropDatabase() {
        superuser.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("set statement_timeout = 0");
                statement.execute("drop database if exists buyer_name_migration with (force)");
            }
            return null;
        });
    }

    @Test
    void givenOrdersPlacedBeforeV40_whenMigrated_thenTheirNameIsSplitAtTheFirstSpace() {
        migrateTo("39");
        var owner = new JdbcTemplate(new DriverManagerDataSource(urlOf(DATABASE), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
        UUID compound = UUID.randomUUID();
        UUID single = UUID.randomUUID();
        UUID spaced = UUID.randomUUID();
        owner.update(INSERT_ORDER, compound, "Ana María Gómez");
        owner.update(INSERT_ORDER, single, "Ana");
        owner.update(INSERT_ORDER, spaced, "Ana  Pérez");

        migrateTo("40");

        assertThat(nameOf(owner, compound)).containsExactly("Ana", "María Gómez");
        assertThat(nameOf(owner, single)).containsExactly("Ana", "-");
        assertThat(nameOf(owner, spaced)).containsExactly("Ana", "Pérez");
    }

    private void migrateTo(String version) {
        Flyway.configure()
            .dataSource(urlOf(DATABASE), "tienda_owner", TestcontainersConfiguration.OWNER_PASSWORD)
            .locations("classpath:db/migration")
            .target(version)
            .load()
            .migrate();
    }

    private static List<Object> nameOf(JdbcTemplate owner, UUID order) {
        Map<String, Object> row = owner.queryForMap(
            "select customer_first_name, customer_last_name from orders where id = ?", order);
        return List.of(row.get("customer_first_name"), row.get("customer_last_name"));
    }

    private String urlOf(String database) {
        return postgres.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
    }
}
