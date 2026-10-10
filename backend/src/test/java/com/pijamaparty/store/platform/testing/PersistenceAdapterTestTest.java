package com.pijamaparty.store.platform.testing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

@PersistenceAdapterTest
class PersistenceAdapterTestTest {

    private final JdbcTemplate jdbc;

    PersistenceAdapterTestTest(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Test
    void givenAPersistenceContractTest_whenItRuns_thenItTalksToPostgresqlNotAnEmbeddedDatabase() {
        String version = jdbc.queryForObject("select version()", String.class);

        assertThat(version).startsWith("PostgreSQL");
    }
}
