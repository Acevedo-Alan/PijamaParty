package com.pijamaparty.store.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * T-E6-19 (RNF-28): the review of what {@code tienda_app} can do, over every table, view, sequence and function of the
 * schema the migrations built, so a migration that grants too much fails here. The checks read the catalog as the
 * container's superuser and ask PostgreSQL itself, so privileges that come through {@code PUBLIC} count too.
 */
@ApiTest
class ApplicationRolePrivilegesTest {

    private final JdbcTemplate admin;
    private final String database;

    ApplicationRolePrivilegesTest(PostgreSQLContainer<?> postgres) {
        this.admin = new JdbcTemplate(
            new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        this.database = postgres.getDatabaseName();
    }

    @Test
    void givenEveryTableAndView_whenCheckingTheApplicationRole_thenItHasNoPrivilegeBeyondReadingAndWritingRows() {
        for (String privilege : List.of("TRUNCATE", "REFERENCES", "TRIGGER")) {
            assertThat(relationsWhereAppHas(privilege)).as(privilege).isEmpty();
        }
    }

    @Test
    void givenTheViewsAndTheMigrationHistory_whenCheckingTheApplicationRole_thenViewsAreReadOnlyAndHistoryIsHidden() {
        List<String> writableViews = admin.queryForList("""
            select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
             where n.nspname = 'public' and c.relkind in ('v', 'm')
               and (has_table_privilege('tienda_app', c.oid, 'INSERT')
                 or has_table_privilege('tienda_app', c.oid, 'UPDATE')
                 or has_table_privilege('tienda_app', c.oid, 'DELETE'))""", String.class);

        assertThat(writableViews).isEmpty();
        assertThat(admin.queryForObject(
            "select has_table_privilege('tienda_app', 'flyway_schema_history', 'SELECT')", Boolean.class)).isFalse();
    }

    @Test
    void givenTheSchemaAndTheDatabase_whenCheckingTheApplicationRole_thenItCannotCreateObjects() {
        assertThat(admin.queryForObject("select has_schema_privilege('tienda_app', 'public', 'CREATE')",
            Boolean.class)).isFalse();
        assertThat(admin.queryForObject("select has_database_privilege('tienda_app', ?, 'CREATE')", Boolean.class,
            database)).isFalse();
        assertThat(admin.queryForObject("select has_database_privilege('tienda_app', ?, 'TEMPORARY')", Boolean.class,
            database)).as("temporary tables").isFalse();
    }

    @Test
    void givenTheSequences_whenCheckingTheApplicationRole_thenItOwnsNone() {
        assertThat(admin.queryForList("""
            select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
              join pg_roles r on r.oid = c.relowner
             where n.nspname = 'public' and c.relkind = 'S' and r.rolname = 'tienda_app'""", String.class)).isEmpty();
    }

    @Test
    void givenTheSecurityDefinerFunctions_whenCheckingThem_thenEachPinsItsSearchPathAndIsNotCallableByPublic() {
        List<String> unpinned = admin.queryForList("""
            select p.oid::regprocedure::text from pg_proc p join pg_namespace n on n.oid = p.pronamespace
             where n.nspname = 'public' and p.prosecdef
               and not exists (select 1 from unnest(coalesce(p.proconfig, '{}')) setting
                                where setting like 'search_path=%')""", String.class);
        List<String> callableByPublic = admin.queryForList("""
            select p.oid::regprocedure::text from pg_proc p join pg_namespace n on n.oid = p.pronamespace
             where n.nspname = 'public' and p.prosecdef
               and (p.proacl is null
                 or exists (select 1 from aclexplode(p.proacl) acl
                             where acl.grantee = 0 and acl.privilege_type = 'EXECUTE'))""", String.class);

        assertThat(unpinned).as("SECURITY DEFINER functions without a fixed search_path").isEmpty();
        assertThat(callableByPublic).as("SECURITY DEFINER functions executable by PUBLIC").isEmpty();
    }

    @Test
    void givenTheApplicationRole_whenCheckingItsMemberships_thenItBelongsToNoOtherRole() {
        assertThat(admin.queryForList("""
            select r.rolname from pg_auth_members m join pg_roles r on r.oid = m.roleid
             where m.member = (select oid from pg_roles where rolname = 'tienda_app')""", String.class)).isEmpty();
    }

    private List<String> relationsWhereAppHas(String privilege) {
        return admin.queryForList("""
            select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
             where n.nspname = 'public' and c.relkind in ('r', 'p', 'v', 'm', 'f')
               and has_table_privilege('tienda_app', c.oid, ?)
             order by c.relname""", String.class, privilege);
    }
}
