package com.pijamaparty.store.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** T-E0-13 with the real deploy/db/init/01-roles.sh: least privilege and separate roles (RNF-27, RNF-28, RNF-29.2). */
@ApiTest
class DatabaseRolesTest {

    private final JdbcTemplate app;
    private final PostgreSQLContainer<?> postgres;

    DatabaseRolesTest(JdbcTemplate jdbc, PostgreSQLContainer<?> postgres) {
        this.app = jdbc;
        this.postgres = postgres;
    }

    @Test
    void givenTheApplicationRole_whenCheckingItsAttributes_thenItIsNotOwnerNorSuperuserNorBypassesRls() {
        Map<String, Object> role = app.queryForMap("""
            select current_user as name, rolsuper, rolbypassrls, rolcreatedb, rolcreaterole
              from pg_roles where rolname = current_user""");
        Long owned = app.queryForObject("""
            select count(*) from pg_tables where schemaname = 'public' and tableowner = current_user""", Long.class);

        assertThat(role).containsEntry("name", "tienda_app")
            .containsEntry("rolsuper", false).containsEntry("rolbypassrls", false)
            .containsEntry("rolcreatedb", false).containsEntry("rolcreaterole", false);
        assertThat(owned).isZero();
    }

    @Test
    void givenTheMigrations_whenTheyRan_thenTheOwnerRoleRanThemAndOwnsTheTables() {
        JdbcTemplate owner = as("tienda_owner", TestcontainersConfiguration.OWNER_PASSWORD);

        assertThat(owner.queryForList("select distinct installed_by from flyway_schema_history", String.class))
            .containsExactly("tienda_owner");
        assertThat(app.queryForList("select distinct tableowner from pg_tables where schemaname = 'public'",
            String.class)).containsExactly("tienda_owner");
    }

    @Test
    void givenTheMigrationHistory_whenTheApplicationTriesToReadOrForgeIt_thenItIsDenied() {
        assertThatThrownBy(() -> app.queryForObject("select count(*) from flyway_schema_history", Long.class))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
        assertThatThrownBy(() -> app.update("delete from flyway_schema_history"))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
    }

    @Test
    void givenTheApplicationRole_whenItTriesDdl_thenItIsDenied() {
        assertThatThrownBy(() -> app.execute("create table intruder (id int)"))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
        assertThatThrownBy(() -> app.execute("drop table spring_session_attributes"))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("must be owner");
    }

    @Test
    void givenATableCreatedByTheMigrations_whenTheApplicationWritesRows_thenItIsAllowed() {
        app.update("""
            insert into spring_session (primary_id, session_id, creation_time, last_access_time,
                                        max_inactive_interval, expiry_time)
            values ('00000000-0000-0000-0000-0000000000aa', '00000000-0000-0000-0000-0000000000bb', 0, 0, 60, 0)""");

        assertThat(app.update("delete from spring_session where primary_id = ?",
            "00000000-0000-0000-0000-0000000000aa")).isEqualTo(1);
    }

    @Test
    void givenTheRoles_whenCheckingTheirTimeouts_thenOnlyTheApplicationIsBoundedByRnf21() {
        assertThat(app.queryForObject("show statement_timeout", String.class)).isEqualTo("10s");
        assertThat(as("tienda_owner", TestcontainersConfiguration.OWNER_PASSWORD)
            .queryForObject("show statement_timeout", String.class)).isEqualTo("0");
        assertThat(as("tienda_backup", TestcontainersConfiguration.BACKUP_PASSWORD)
            .queryForObject("show statement_timeout", String.class)).isEqualTo("0");
    }

    @Test
    void givenTheBackupRole_whenItReadsAndWrites_thenItCanOnlyRead() {
        JdbcTemplate backup = as("tienda_backup", TestcontainersConfiguration.BACKUP_PASSWORD);

        assertThat(backup.queryForObject("select count(*) from spring_session", Long.class)).isNotNull();
        assertThatThrownBy(() -> backup.update("delete from spring_session"))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
    }

    @Test
    void givenTheApplicationConnectedAsASuperuser_whenItStarts_thenTheRoleGuardRefusesToStart() {
        var superuser = new ApplicationRoleGuard(as(postgres.getUsername(), postgres.getPassword()));

        assertThatThrownBy(superuser::verify).isInstanceOf(IllegalStateException.class).hasMessageContaining("RNF-29.2");
    }

    @Test
    void givenTheApplicationConnectedAsTheMigrationRole_whenItStarts_thenTheRoleGuardRefusesToStart() {
        var owner = new ApplicationRoleGuard(as("tienda_owner", TestcontainersConfiguration.OWNER_PASSWORD));

        assertThatThrownBy(owner::verify).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("rolbypassrls").hasMessageContaining("owns_relations");
    }

    @Test
    void givenARoleThatOwnsATableWithoutBypassRls_whenTheApplicationUsesIt_thenTheRoleGuardRefusesToStart() {
        JdbcTemplate admin = as(postgres.getUsername(), postgres.getPassword());
        admin.execute("create role guard_table_owner login nobypassrls password 'guard-test-only'");
        admin.execute("create table guard_owned (id int)");
        admin.execute("alter table guard_owned owner to guard_table_owner");
        admin.execute("grant connect on database " + postgres.getDatabaseName() + " to guard_table_owner");
        try {
            var guard = new ApplicationRoleGuard(as("guard_table_owner", "guard-test-only"));

            assertThatThrownBy(guard::verify).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owns_relations").hasMessageNotContaining("rolbypassrls");
        } finally {
            admin.execute("drop table guard_owned");
            admin.execute("revoke connect on database " + postgres.getDatabaseName() + " from guard_table_owner");
            admin.execute("drop role guard_table_owner");
        }
    }

    @Test
    void givenARoleThatIsAMemberOfTheOwner_whenTheApplicationUsesIt_thenTheRoleGuardRefusesToStart() {
        JdbcTemplate admin = as(postgres.getUsername(), postgres.getPassword());
        admin.execute("create role guard_member login nobypassrls password 'guard-test-only'");
        admin.execute("grant tienda_owner to guard_member");
        admin.execute("grant connect on database " + postgres.getDatabaseName() + " to guard_member");
        try {
            var guard = new ApplicationRoleGuard(as("guard_member", "guard-test-only"));

            assertThatThrownBy(guard::verify).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("privileged_membership").hasMessageContaining("owns_relations");
        } finally {
            admin.execute("revoke connect on database " + postgres.getDatabaseName() + " from guard_member");
            admin.execute("drop role guard_member");
        }
    }

    private JdbcTemplate as(String user, String password) {
        return new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), user, password));
    }
}
