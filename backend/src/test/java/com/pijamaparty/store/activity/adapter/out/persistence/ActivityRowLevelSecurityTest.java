package com.pijamaparty.store.activity.adapter.out.persistence;

import static com.pijamaparty.store.platform.testing.RlsPolicyTester.ADMIN;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.ADMIN_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_B;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * T-E8-01: the Row Level Security policies of {@code activity_entry} (V41, RNF-27), with customer A, customer B, a
 * guest, the administrator, the system and a missing context. The administrator reads every entry and records its own;
 * the system records only the entries of signing in, and reads and deletes only the entries older than 2 years; nobody
 * else reaches anything, and nobody edits an entry. Every transaction is committed by the application role.
 */
@ApiTest
class ActivityRowLevelSecurityTest {

    private static final String INSERT = """
        insert into activity_entry (id, occurred_at, actor_id, area, action, subject_type, changes)
        values (?, now() - cast(? as interval), ?, ?, ?, 'SHIPPING', '[]'::jsonb)
        """;
    private static final String RLS_VIOLATION = "row-level security";

    private final RlsPolicyTester rls;
    private final JdbcTemplate owner;
    private final UUID recent = UUID.randomUUID();
    private final UUID old = UUID.randomUUID();

    ActivityRowLevelSecurityTest(PlatformTransactionManager transactionManager, JdbcTemplate jdbc,
                                 PostgreSQLContainer<?> postgres) {
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @BeforeEach
    void anEntryOfTodayAndOneOfThreeYearsAgo() {
        owner.update(INSERT, recent, "1 hour", ADMIN_ID.value(), "STOCK", "STOCK_ADJUSTED");
        owner.update(INSERT, old, "3 years", ADMIN_ID.value(), "STOCK", "STOCK_ADJUSTED");
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate activity_entry");
    }

    /** Who runs the transaction ({@code actor} empty: no security context) and the entries it reads. */
    record Caller(String name, Optional<Actor> actor, Set<String> reads) {

        <T> T run(RlsPolicyTester rls, Function<JdbcTemplate, T> work) {
            return actor.map(a -> rls.as(a, work)).orElseGet(() -> rls.withoutContext(work));
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Caller> callers() {
        return Stream.of(
            new Caller("customer A", Optional.of(CUSTOMER_A), Set.of()),
            new Caller("customer B", Optional.of(CUSTOMER_B), Set.of()),
            new Caller("guest", Optional.of(GUEST), Set.of()),
            new Caller("administrator", Optional.of(ADMIN), Set.of("recent", "old")),
            new Caller("system", Optional.of(SYSTEM), Set.of("old")),
            new Caller("no context", Optional.empty(), Set.of()));
    }

    static Stream<Caller> withoutAccess() {
        return callers().filter(caller -> caller.reads().isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenEntriesOfTodayAndOfThreeYearsAgo_whenACallerReadsThem_thenItSeesOnlyWhatItsPolicyAllows(Caller caller) {
        List<UUID> seen = caller.run(rls, sql -> sql.queryForList("select id from activity_entry", UUID.class));

        assertThat(seen).containsExactlyInAnyOrderElementsOf(caller.reads().stream()
            .map(which -> which.equals("old") ? old : recent).toList());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("withoutAccess")
    void givenACallerWithoutAccess_whenItRecordsAnEntry_thenRowLevelSecurityRefusesIt(Caller caller) {
        assertThatThrownBy(() -> caller.run(rls, sql -> sql.update(INSERT, UUID.randomUUID(), "0 seconds",
            CUSTOMER_A_ID.value(), "ACCOUNT", "ADMIN_SIGNED_IN")))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
    }

    @Test
    void givenTheAdministrator_whenItRecordsItsOwnChange_thenItIsStored_butNotOneInAnotherUsersName() {
        UUID own = UUID.randomUUID();

        rls.as(ADMIN, sql -> sql.update(INSERT, own, "0 seconds", ADMIN_ID.value(), "CATALOG", "PRODUCT_HIDDEN"));

        assertThat(owner.queryForObject("select count(*) from activity_entry where id = ?", Integer.class, own))
            .isOne();
        assertThatThrownBy(() -> rls.as(ADMIN, sql -> sql.update(INSERT, UUID.randomUUID(), "0 seconds",
            CUSTOMER_A_ID.value(), "CATALOG", "PRODUCT_HIDDEN")))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
    }

    @Test
    void givenTheSystem_whenItRecordsASignIn_thenItIsStored_butAnyOtherAreaIsRefused() {
        UUID signIn = UUID.randomUUID();

        rls.as(SYSTEM, sql -> sql.update(INSERT, signIn, "0 seconds", ADMIN_ID.value(), "ACCOUNT", "ADMIN_SIGNED_IN"));

        assertThat(owner.queryForObject("select count(*) from activity_entry where id = ?", Integer.class, signIn))
            .isOne();
        assertThatThrownBy(() -> rls.as(SYSTEM, sql -> sql.update(INSERT, UUID.randomUUID(), "0 seconds",
            ADMIN_ID.value(), "CATALOG", "PRODUCT_HIDDEN")))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenAnyCaller_whenItEditsAnEntry_thenThePrivilegeIsMissing(Caller caller) {
        assertThatThrownBy(() -> caller.run(rls, sql -> sql.update(
            "update activity_entry set action = 'PRODUCT_PUBLISHED' where id = ?", old)))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenAnyCaller_whenItDeletesEveryEntry_thenOnlyTheSystemDeletes_andOnlyTheOldOne(Caller caller) {
        int deleted = caller.run(rls, sql -> sql.update("delete from activity_entry"));

        boolean system = caller.actor().filter(actor -> actor.equals(SYSTEM)).isPresent();
        assertThat(deleted).isEqualTo(system ? 1 : 0);
        assertThat(owner.queryForList("select id from activity_entry", UUID.class))
            .containsExactlyInAnyOrderElementsOf(system ? List.of(recent) : List.of(recent, old));
    }
}
