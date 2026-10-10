package com.pijamaparty.store.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ApiTest;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** T-E0-03 on a fresh Testcontainers PostgreSQL: Flyway migrates on startup and the pool follows RNF-21. */
@ApiTest
class DatabaseBaselineTest {

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    DatabaseBaselineTest(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Test
    void givenAnEmptyDatabase_whenTheBackendStarts_thenTheMigrationsCreateTheSchemaWithoutIntervention() {
        Integer applied = jdbc.queryForObject(
            "select count(*) from flyway_schema_history where version = '1' and success", Integer.class);
        var tables = jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema = 'public'", String.class);

        assertThat(applied).isEqualTo(1);
        assertThat(tables).contains("event_publication", "event_publication_archive",
            "spring_session", "spring_session_attributes");
    }

    @Test
    void givenTheConnectionPool_whenTheApplicationRuns_thenItIsBoundedAsRnf21Says() throws Exception {
        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);

        assertThat(hikari.getMaximumPoolSize()).isEqualTo(8);
        assertThat(hikari.getMinimumIdle()).isEqualTo(2);
        assertThat(Duration.ofMillis(hikari.getConnectionTimeout())).isEqualTo(Duration.ofSeconds(3));
        assertThat(Duration.ofMillis(hikari.getMaxLifetime())).isEqualTo(Duration.ofMinutes(25));
        assertThat(hikari.getLeakDetectionThreshold()).as("leak detection only in dev").isZero();
    }

    @Test
    void givenTheDatabaseServer_whenQueryingItsLimits_thenTheyFollowRnf21() {
        assertThat(jdbc.queryForObject("show statement_timeout", String.class)).isEqualTo("10s");
        assertThat(jdbc.queryForObject("show idle_in_transaction_session_timeout", String.class)).isEqualTo("30s");
        assertThat(jdbc.queryForObject("show max_connections", String.class)).isEqualTo("30");
    }
}
