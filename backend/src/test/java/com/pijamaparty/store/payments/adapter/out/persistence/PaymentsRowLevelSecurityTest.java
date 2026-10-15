package com.pijamaparty.store.payments.adapter.out.persistence;

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
 * T-E3-21: the Row Level Security policies of {@code payment} (V15) and {@code payment_event} (V22), tested for reads,
 * inserts, updates and deletes with customer A, customer B, a guest, the administrator and the system, plus a missing
 * context, a guest without a token and a customer holding a guest's token (RNF-27). Payments are no buyer's: the
 * system creates, reads and updates them and records each notification once, the administrator reads both tables,
 * everybody else reaches nothing, and nobody deletes a payment or changes a notification (R-15). Every transaction is
 * committed by the application role.
 */
@ApiTest
class PaymentsRowLevelSecurityTest {

    private static final String INSERT_PAYMENT = """
        insert into payment (id, order_id, order_number, provider, amount_cents, status, created_at, updated_at)
        values (?, ?, nextval('order_number_seq'), 'MERCADOPAGO', 2100000, 'PENDING', now(), now())
        """;
    private static final String INSERT_EVENT = """
        insert into payment_event (provider, external_payment_id, status, order_number, received_at)
        values ('MERCADOPAGO', ?, 'APPROVED', 1001, now())
        """;
    private static final String RLS_VIOLATION = "row-level security";
    private static final String PERMISSION_DENIED = "permission denied";

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

    PaymentsRowLevelSecurityTest(PlatformTransactionManager transactionManager, JdbcTemplate jdbc,
                                 PostgreSQLContainer<?> postgres) {
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate payment_event, payment");
    }

    @Test
    void givenThePaymentTables_whenCheckingTheirProtection_thenRowLevelSecurityIsEnabledAndForced() {
        for (String table : List.of("payment", "payment_event")) {
            var flags = owner.queryForMap(
                "select relrowsecurity, relforcerowsecurity from pg_class where oid = ?::regclass", table);
            assertThat(flags).as(table).containsEntry("relrowsecurity", true).containsEntry("relforcerowsecurity", true);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenTwoPaymentsAndTheirNotifications_whenReading_thenOnlyTheAdministratorAndTheSystemSeeThem(
            Caller caller) {
        var payments = seed();

        var visiblePayments = caller.run(rls, sql -> sql.queryForList("select id from payment", UUID.class));
        var visibleEvents = caller.run(rls, sql -> sql.queryForList(
            "select external_payment_id from payment_event", String.class));

        assertThat(visiblePayments).as("payments").containsExactlyInAnyOrderElementsOf(
            caller.reads() ? payments : List.of());
        assertThat(visibleEvents).as("notifications").containsExactlyInAnyOrderElementsOf(
            caller.reads() ? List.of("111", "222") : List.of());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenNoPayment_whenCreatingAPaymentAndRecordingANotification_thenOnlyTheSystemIsAccepted(Caller caller) {
        var create = (Function<JdbcTemplate, Integer>) sql -> sql.update(INSERT_PAYMENT, UUID.randomUUID(),
            UUID.randomUUID());
        var record = (Function<JdbcTemplate, Integer>) sql -> sql.update(INSERT_EVENT, "333");

        for (var write : List.of(create, record)) {
            if (caller.writes()) {
                assertThat(caller.run(rls, write)).isOne();
            } else {
                assertThatThrownBy(() -> caller.run(rls, write))
                    .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
            }
        }
    }

    @Test
    void givenARecordedNotification_whenTheSystemRecordsItAgainSkippingConflicts_thenNothingIsAddedOrRaised() {
        seed();

        int recorded = rls.as(SYSTEM, sql -> sql.update(INSERT_EVENT + " on conflict do nothing", "111"));

        assertThat(recorded).isZero();
        assertThat(owner.queryForObject("select count(*) from payment_event", Integer.class)).isEqualTo(2);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenTwoPayments_whenUpdatingThem_thenOnlyTheSystemChangesThem(Caller caller) {
        var payments = seed();

        var updated = caller.run(rls, sql -> sql.queryForList(
            "update payment set status = 'REJECTED', updated_at = now() returning id", UUID.class));

        var expected = caller.writes() ? payments : List.<UUID>of();
        assertThat(updated).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(owner.queryForList("select id from payment where status = 'REJECTED'", UUID.class)).as("stored")
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenTwoPaymentsAndTheirNotifications_whenDeletingOrChangingThem_thenItIsDenied(Caller caller) {
        seed();

        for (String sql : List.of("delete from payment", "update payment_event set status = 'OTHER'",
            "delete from payment_event")) {
            assertThatThrownBy(() -> caller.run(rls, jdbc -> jdbc.update(sql))).as(sql)
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining(PERMISSION_DENIED);
        }
        assertThat(owner.queryForObject("select count(*) from payment", Integer.class)).isEqualTo(2);
        assertThat(owner.queryForObject("select count(*) from payment_event where status = 'APPROVED'",
            Integer.class)).isEqualTo(2);
    }

    /** Two payments and two notifications, stored by the owner role (no policies); answers the payment ids. */
    private List<UUID> seed() {
        var payments = List.of(UUID.randomUUID(), UUID.randomUUID());
        for (UUID payment : payments) {
            owner.update(INSERT_PAYMENT, payment, UUID.randomUUID());
        }
        owner.update(INSERT_EVENT, "111");
        owner.update(INSERT_EVENT, "222");
        return payments;
    }
}
