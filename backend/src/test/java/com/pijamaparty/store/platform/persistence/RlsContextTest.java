package com.pijamaparty.store.platform.persistence;

import static com.pijamaparty.store.platform.testing.RlsPolicyTester.ADMIN;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_B;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_B_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST_TRACKING;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import jakarta.persistence.EntityManagerFactory;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * T-E0-14 on a probe table protected like the business tables will be: FORCE ROW LEVEL SECURITY and policies built
 * on the app_*() context functions of V2 (RNF-27).
 */
@ApiTest
class RlsContextTest {

    private static final String PROBE = """
        create table rls_probe (label text primary key, owner_id uuid, tracking_token_hash text);
        alter table rls_probe enable row level security;
        alter table rls_probe force row level security;
        create policy probe_customer on rls_probe to tienda_app
            using (app_role() = 'CUSTOMER' and owner_id = app_user_id());
        create policy probe_guest on rls_probe to tienda_app
            using (app_role() = 'ANONYMOUS' and tracking_token_hash = app_tracking_token_hash());
        create policy probe_staff on rls_probe to tienda_app
            using (app_role() in ('ADMIN', 'SYSTEM'));
        """;

    private final RlsPolicyTester rls;
    private final JdbcTemplate app;
    private final JdbcTemplate owner;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;
    private final DataSource dataSource;

    RlsContextTest(PlatformTransactionManager transactionManager, JdbcTemplate jdbc, PostgreSQLContainer<?> postgres,
                   EntityManagerFactory entityManagerFactory, DataSource dataSource) {
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.transactionManager = transactionManager;
        this.entityManagerFactory = entityManagerFactory;
        this.dataSource = dataSource;
        this.app = jdbc;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @BeforeEach
    void createProbeTable() {
        owner.execute(PROBE);
        owner.update("insert into rls_probe values ('of A', ?, null), ('of B', ?, null), ('of guest', null, ?)",
            CUSTOMER_A_ID.value(), CUSTOMER_B_ID.value(), GUEST_TRACKING.hash());
    }

    @AfterEach
    void dropProbeTable() {
        owner.execute("drop table rls_probe");
    }

    @Test
    void givenAQueryWithoutSecurityContext_whenItRunsOnAProtectedTable_thenItReturnsNoRows() {
        assertThat(rls.withoutContext(this::labels)).isEmpty();
        assertThat(labels(app)).as("outside any transaction there is no context either").isEmpty();
    }

    @Test
    void givenTwoCustomers_whenEachQueries_thenEachSeesOnlyItsOwnRows() {
        assertThat(rls.as(CUSTOMER_A, this::labels)).containsExactly("of A");
        assertThat(rls.as(CUSTOMER_B, this::labels)).containsExactly("of B");
    }

    @Test
    void givenAGuest_whenQueryingWithAndWithoutItsToken_thenItSeesOnlyTheRowOfItsToken() {
        assertThat(rls.as(GUEST, this::labels)).containsExactly("of guest");
        assertThat(rls.as(Actor.anonymous(Optional.empty(), Optional.empty()), this::labels)).isEmpty();
    }

    @Test
    void givenTheAdministratorAndTheSystem_whenQuerying_thenTheySeeEveryRow() {
        assertThat(rls.as(ADMIN, this::labels)).containsExactlyInAnyOrder("of A", "of B", "of guest");
        assertThat(rls.as(SYSTEM, this::labels)).containsExactlyInAnyOrder("of A", "of B", "of guest");
    }

    @Test
    void givenATransactionAsAdministrator_whenTheNextTransactionHasNoActor_thenTheContextDidNotLeak() {
        rls.as(ADMIN, this::labels);

        for (int i = 0; i < 10; i++) {
            assertThat(rls.withoutContext(this::labels)).isEmpty();
        }
    }

    @Test
    void givenAGuestToken_whenTheContextIsSet_thenTheDatabaseSeesOnlyItsHash() {
        String seen = rls.as(GUEST, sql -> sql.queryForObject("select app_tracking_token_hash()", String.class));

        assertThat(seen).isEqualTo(GUEST_TRACKING.hash()).doesNotContain(GUEST_TRACKING.value());
    }

    @Test
    void givenWorkRunAsSystem_whenItEnds_thenThePreviousActorIsRestored() {
        CurrentActor.runAs(CUSTOMER_A, () -> {
            assertThat(CurrentActor.callAsSystem(() -> CurrentActor.get().orElseThrow())).isEqualTo(SYSTEM);
            assertThat(CurrentActor.get()).contains(CUSTOMER_A);
        });

        assertThat(CurrentActor.get()).isEmpty();
    }

    @Test
    void givenAReadOnlyTransaction_whenACustomerQueries_thenTheContextIsAppliedToo() {
        var readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);

        List<String> seen = CurrentActor.callAs(CUSTOMER_A, () -> readOnly.execute(status -> labels(app)));

        assertThat(seen).containsExactly("of A");
    }

    @Test
    void givenAnActiveTransaction_whenCodeTriesToSwitchToSystemInsideIt_thenItIsRefused() {
        var transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> CurrentActor.runAs(CUSTOMER_A, () -> transaction.executeWithoutResult(
            status -> CurrentActor.runAsSystem(() -> labels(app)))))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("active transaction");
    }

    @Test
    void givenTheContextCannotBeSet_whenATransactionBegins_thenItFailsAndReleasesTheConnectionAndTheThread()
        throws Exception {
        var failing = new RlsTransactionManager(entityManagerFactory) {
            private static final long serialVersionUID = 1L;

            @Override
            void applyContext(java.sql.Connection connection, Optional<Actor> actor) throws SQLException {
                throw new SQLException("simulated failure while setting the context");
            }
        };
        failing.afterPropertiesSet();
        HikariPoolMXBean pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
        int activeBefore = pool.getActiveConnections();

        assertThatThrownBy(() -> new TransactionTemplate(failing).executeWithoutResult(status -> labels(app)))
            .isInstanceOf(CannotCreateTransactionException.class);

        assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(pool.getActiveConnections()).isEqualTo(activeBefore);
        assertThat(rls.as(CUSTOMER_A, this::labels)).as("the thread still works").containsExactly("of A");
    }

    private List<String> labels(JdbcTemplate sql) {
        return sql.queryForList("select label from rls_probe order by label", String.class);
    }
}
