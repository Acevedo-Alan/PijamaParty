package com.pijamaparty.store.platform.adapter.out;

import static com.pijamaparty.store.platform.testing.RlsPolicyTester.ADMIN;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_A_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_B;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.CUSTOMER_B_ID;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.GUEST_CART;
import static com.pijamaparty.store.platform.testing.RlsPolicyTester.SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
 * T-E3-21: the Row Level Security policies of {@code idempotency_key} (V21), tested for reads, inserts, updates and
 * deletes with customer A, customer B, a guest, the administrator and the system, plus a missing context, a guest
 * without a token and a customer holding a guest's cart token (RNF-27). A customer reads and records only the keys of
 * its user, a guest only the keys of its cart token, the system reads and purges every key, the administrator and a
 * missing context reach nothing, and nobody changes a stored answer. Every transaction is committed by the application
 * role.
 */
@ApiTest
class IdempotencyKeyRowLevelSecurityTest {

    private static final String INSERT_KEY = """
        insert into idempotency_key (scope, idempotency_key, request_hash, resource_id, response_status, response_body,
                                     created_at, expires_at)
        values (?, gen_random_uuid(), ?, gen_random_uuid(), 201, '{}', now(), now() + interval '24 hours')
        """;
    private static final String RLS_VIOLATION = "row-level security";

    /** The scopes of the seeded keys (RNF-29.6: a guest's scope is its cart token hash). */
    enum Scope {
        CUSTOMER_A("USER:" + CUSTOMER_A_ID.value()),
        CUSTOMER_B("USER:" + CUSTOMER_B_ID.value()),
        GUEST("CART:" + GUEST_CART.hash()),
        OTHER_GUEST("CART:" + new CartToken("another-guest-cart-token").hash());

        private final String value;

        Scope(String value) {
            this.value = value;
        }

        static Scope of(String value) {
            return Stream.of(values()).filter(s -> s.value.equals(value)).findFirst().orElseThrow();
        }
    }

    /**
     * Who runs the transaction ({@code actor} empty: no security context), the scopes it owns and whether it is the
     * purge, which reads and deletes every key but records none.
     */
    record Caller(String name, Optional<Actor> actor, Set<Scope> owns, boolean purges) {

        <T> T run(RlsPolicyTester rls, Function<JdbcTemplate, T> work) {
            return actor.map(a -> rls.as(a, work)).orElseGet(() -> rls.withoutContext(work));
        }

        Set<Scope> reads() {
            return purges ? EnumSet.allOf(Scope.class) : owns;
        }

        Set<Scope> deletes() {
            return purges ? EnumSet.allOf(Scope.class) : EnumSet.noneOf(Scope.class);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Caller> callers() {
        return Stream.of(
            new Caller("customer A", Optional.of(CUSTOMER_A), EnumSet.of(Scope.CUSTOMER_A), false),
            new Caller("customer B", Optional.of(CUSTOMER_B), EnumSet.of(Scope.CUSTOMER_B), false),
            new Caller("guest", Optional.of(GUEST), EnumSet.of(Scope.GUEST), false),
            new Caller("administrator", Optional.of(ADMIN), EnumSet.noneOf(Scope.class), false),
            new Caller("system", Optional.of(SYSTEM), EnumSet.noneOf(Scope.class), true),
            new Caller("no context", Optional.empty(), EnumSet.noneOf(Scope.class), false),
            new Caller("guest without token", Optional.of(Actor.anonymous(Optional.empty(), Optional.empty())),
                EnumSet.noneOf(Scope.class), false),
            new Caller("customer A holding the guest's token",
                Optional.of(Actor.customer(CUSTOMER_A_ID, Optional.of(GUEST_CART), Optional.empty())),
                EnumSet.of(Scope.CUSTOMER_A), false));
    }

    private final RlsPolicyTester rls;
    private final JdbcTemplate owner;

    IdempotencyKeyRowLevelSecurityTest(PlatformTransactionManager transactionManager, JdbcTemplate jdbc,
                                       PostgreSQLContainer<?> postgres) {
        this.rls = new RlsPolicyTester(transactionManager, jdbc);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate idempotency_key");
    }

    @Test
    void givenTheKeyTable_whenCheckingItsProtection_thenRowLevelSecurityIsEnabledAndForced() {
        var flags = owner.queryForMap(
            "select relrowsecurity, relforcerowsecurity from pg_class where oid = 'idempotency_key'::regclass");

        assertThat(flags).containsEntry("relrowsecurity", true).containsEntry("relforcerowsecurity", true);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenAKeyOfEveryScope_whenReading_thenOnlyTheReachableScopesAreVisible(Caller caller) {
        seedKeys();

        var visible = caller.run(rls, sql -> sql.queryForList("select scope from idempotency_key", String.class));

        assertThat(scopes(visible)).isEqualTo(caller.reads());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenNoKey_whenRecordingOneInEveryScope_thenOnlyItsOwnScopeAcceptsIt(Caller caller) {
        for (Scope target : Scope.values()) {
            var record = (Function<JdbcTemplate, Integer>) sql -> sql.update(INSERT_KEY, target.value, "e".repeat(64));
            if (caller.owns().contains(target)) {
                assertThat(caller.run(rls, record)).as(target.name()).isOne();
            } else {
                assertThatThrownBy(() -> caller.run(rls, record)).as(target.name())
                    .isInstanceOf(DataAccessException.class).hasStackTraceContaining(RLS_VIOLATION);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenAKeyOfEveryScope_whenChangingTheStoredAnswers_thenItIsDenied(Caller caller) {
        seedKeys();

        assertThatThrownBy(() -> caller.run(rls, sql -> sql.update(
                "update idempotency_key set response_body = '{\"number\": 1}'")))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
        assertThat(owner.queryForObject("select count(*) from idempotency_key where response_body = '{}'",
            Integer.class)).isEqualTo(Scope.values().length);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callers")
    void givenAKeyOfEveryScope_whenDeletingAllKeys_thenOnlyThePurgeDeletesThem(Caller caller) {
        seedKeys();

        var deleted = caller.run(rls, sql -> sql.queryForList(
            "delete from idempotency_key returning scope", String.class));

        assertThat(scopes(deleted)).isEqualTo(caller.deletes());
        assertThat(scopes(owner.queryForList("select scope from idempotency_key", String.class)))
            .isEqualTo(EnumSet.complementOf(withNone(caller.deletes())));
    }

    /** One key per scope, stored by the owner role (no policies). */
    private void seedKeys() {
        for (Scope scope : Scope.values()) {
            owner.update(INSERT_KEY, scope.value, "e".repeat(64));
        }
    }

    /** The scopes of the given rows; fails on a duplicate, so a key seen twice is not hidden by the set. */
    private static Set<Scope> scopes(List<String> rows) {
        assertThat(rows).doesNotHaveDuplicates();
        return rows.stream().map(Scope::of).collect(Collectors.toCollection(() -> EnumSet.noneOf(Scope.class)));
    }

    /** EnumSet.copyOf refuses an empty plain collection; this keeps the element type for it. */
    private static EnumSet<Scope> withNone(Set<Scope> scopes) {
        return scopes.isEmpty() ? EnumSet.noneOf(Scope.class) : EnumSet.copyOf(scopes);
    }
}
