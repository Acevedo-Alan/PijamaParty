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
 * T-E3-21: the Row Level Security policies of {@code email_outbox} (V19), tested for reads, inserts, updates and
 * deletes with customer A, customer B, a guest, the administrator and the system, plus a missing context, a guest
 * without a token and a customer holding a guest's token (RNF-27). The system enqueues, reads and updates the emails,
 * the administrator reads them for the order's alerts, everybody else reaches nothing, and nobody deletes what was
 * sent. Every transaction is committed by the application role.
 */
@ApiTest
class EmailOutboxRowLevelSecurityTest {

    private static final String INSERT_EMAIL = """
        insert into email_outbox (id, type, reference, status, attempts, next_attempt_at, created_at, updated_at)
        values (?, 'ORDER_PAID_CUSTOMER', gen_random_uuid(), 'PENDING', 0, now(), now(), now())
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

    EmailOutboxRowLevelSecurityTest(PlatformTransactionManager transactionManager, JdbcTemplate jdbc,
                                    PostgreSQLContainer<?> postgres) {
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate email_outbox");
    }

    @Test
    void givenTheOutbox_whenCheckingItsProtection_thenRowLevelSecurityIsEnabledAndForced() {
        var flags = owner.queryForMap(
            "select relrowsecurity, relforcerowsecurity from pg_class where oid = 'email_outbox'::regclass");

        assertThat(flags).containsEntry("relrowsecurity", true).containsEntry("relforcerowsecurity", true);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenTwoEmails_whenReading_thenOnlyTheAdministratorAndTheSystemSeeThem(Caller caller) {
        var emails = seed();

        var visible = caller.run(rls, sql -> sql.queryForList("select id from email_outbox", UUID.class));

        assertThat(visible).containsExactlyInAnyOrderElementsOf(caller.reads() ? emails : List.of());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenNoEmail_whenEnqueueingOne_thenOnlyTheSystemIsAccepted(Caller caller) {
        var enqueue = (Function<JdbcTemplate, Integer>) sql -> sql.update(INSERT_EMAIL, UUID.randomUUID());

        if (caller.writes()) {
            assertThat(caller.run(rls, enqueue)).isOne();
        } else {
            assertThatThrownBy(() -> caller.run(rls, enqueue))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenTwoEmails_whenUpdatingThem_thenOnlyTheSystemChangesThem(Caller caller) {
        var emails = seed();

        var updated = caller.run(rls, sql -> sql.queryForList(
            "update email_outbox set status = 'FAILED', updated_at = now() returning id", UUID.class));

        var expected = caller.writes() ? emails : List.<UUID>of();
        assertThat(updated).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(owner.queryForList("select id from email_outbox where status = 'FAILED'", UUID.class))
            .as("stored").containsExactlyInAnyOrderElementsOf(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenTwoEmails_whenDeletingThem_thenItIsDenied(Caller caller) {
        seed();

        assertThatThrownBy(() -> caller.run(rls, sql -> sql.update("delete from email_outbox")))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
        assertThat(owner.queryForObject("select count(*) from email_outbox", Integer.class)).isEqualTo(2);
    }

    /** Two pending emails, stored by the owner role (no policies); answers their ids. */
    private List<UUID> seed() {
        var emails = List.of(UUID.randomUUID(), UUID.randomUUID());
        for (UUID email : emails) {
            owner.update(INSERT_EMAIL, email);
        }
        return emails;
    }
}
