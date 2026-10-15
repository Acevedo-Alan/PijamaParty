package com.pijamaparty.store.notifications.adapter.out.persistence;

import static com.pijamaparty.store.platform.testing.RlsPolicyTester.ADMIN;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_B;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST_TRACKING;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * T-E3-21, T-E3-22: the Row Level Security policies of {@code email_event} (V23), tested for reads, inserts, updates
 * and deletes with customer A, customer B, a guest, the administrator and the system, plus a missing context, a guest
 * without a token and a customer holding a guest's token (RNF-27). The system records and reads the notifications,
 * the administrator reads them, everybody else reaches nothing, and nobody changes or deletes them (R-15). Every
 * transaction is committed by the application role.
 */
@ApiTest
class EmailEventRowLevelSecurityTest {

    private static final String INSERT_EVENT = """
        insert into email_event (provider, event_id, type, provider_message_id, received_at)
        values ('RESEND', ?, 'PERMANENT_BOUNCE', 're_1', now())
        """;
    private static final String RLS_VIOLATION = "row-level security";

    /** Who runs the transaction ({@code actor} empty: no security context). */
    record Caller(String name, Optional<Actor> actor) {

        <T> T run(RlsPolicyTester rls, Function<JdbcTemplate, T> work) {
            return actor.map(a -> rls.as(a, work)).orElseGet(() -> rls.withoutContext(work));
        }

        boolean reads() {
            return actor.filter(a -> a.is(Role.ADMIN) || a.is(Role.SYSTEM)).isPresent();
        }

        boolean writes() {
            return actor.filter(a -> a.is(Role.SYSTEM)).isPresent();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Caller> callers() {
        return Stream.of(
            new Caller("customer A", Optional.of(CUSTOMER_A)),
            new Caller("customer B", Optional.of(CUSTOMER_B)),
            new Caller("guest", Optional.of(GUEST)),
            new Caller("administrator", Optional.of(ADMIN)),
            new Caller("system", Optional.of(SYSTEM)),
            new Caller("no context", Optional.empty()),
            new Caller("guest without token", Optional.of(Actor.anonymous(Optional.empty(), Optional.empty()))),
            new Caller("customer A holding the guest's token",
                Optional.of(Actor.customer(CUSTOMER_A_ID, Optional.empty(), Optional.of(GUEST_TRACKING)))));
    }

    private final RlsPolicyTester rls;
    private final JdbcTemplate owner;

    EmailEventRowLevelSecurityTest(PlatformTransactionManager transactionManager, JdbcTemplate jdbc,
                                   PostgreSQLContainer<?> postgres) {
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate email_event");
    }

    @Test
    void givenTheEvents_whenCheckingTheirProtection_thenRowLevelSecurityIsEnabledAndForced() {
        var flags = owner.queryForMap(
            "select relrowsecurity, relforcerowsecurity from pg_class where oid = 'email_event'::regclass");

        assertThat(flags).containsEntry("relrowsecurity", true).containsEntry("relforcerowsecurity", true);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenTwoEvents_whenReading_thenOnlyTheAdministratorAndTheSystemSeeThem(Caller caller) {
        var events = seed();

        var visible = caller.run(rls, sql -> sql.queryForList("select event_id from email_event", String.class));

        assertThat(visible).containsExactlyInAnyOrderElementsOf(caller.reads() ? events : List.of());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenNoEvent_whenRecordingOne_thenOnlyTheSystemIsAccepted(Caller caller) {
        var record = (Function<JdbcTemplate, Integer>) sql -> sql.update(INSERT_EVENT, "msg_" + UUID.randomUUID());

        if (caller.writes()) {
            assertThat(caller.run(rls, record)).isOne();
        } else {
            assertThatThrownBy(() -> caller.run(rls, record))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenTwoEvents_whenUpdatingThem_thenItIsDenied(Caller caller) {
        seed();

        assertThatThrownBy(() -> caller.run(rls, sql -> sql.update("update email_event set type = 'COMPLAINT'")))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
        assertThat(owner.queryForObject("select count(*) from email_event where type = 'COMPLAINT'", Integer.class))
            .isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenTwoEvents_whenDeletingThem_thenItIsDenied(Caller caller) {
        seed();

        assertThatThrownBy(() -> caller.run(rls, sql -> sql.update("delete from email_event")))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
        assertThat(owner.queryForObject("select count(*) from email_event", Integer.class)).isEqualTo(2);
    }

    /** Two notifications, stored by the owner role (no policies); answers their event ids. */
    private List<String> seed() {
        var events = List.of("msg_" + UUID.randomUUID(), "msg_" + UUID.randomUUID());
        for (String event : events) {
            owner.update(INSERT_EVENT, event);
        }
        return events;
    }
}
