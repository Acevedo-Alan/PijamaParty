package com.pijamaparty.store.platform.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.application.ExpiredIdempotencyKeys;
import com.pijamaparty.store.platform.application.IdempotencyKeys;
import com.pijamaparty.store.platform.application.IdempotencyKeysContractTest;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.IdempotencyScope;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Each call committed in its own transaction as the actor of the scope, and the purge as the system, so Row Level
 * Security applies as in production (RNF-27), then read back from PostgreSQL.
 */
@ApiTest
class JdbcIdempotencyKeyStoreContractTest
    extends IdempotencyKeysContractTest<JdbcIdempotencyKeyStoreContractTest.AsScopeOwner> {

    private static final Actor CUSTOMER_ACTOR = Actor.customer(CUSTOMER_ID, Optional.empty(), Optional.empty());
    private static final Actor GUEST_ACTOR = Actor.anonymous(Optional.of(GUEST_CART), Optional.empty());
    private static final Actor OTHER_GUEST =
        Actor.anonymous(Optional.of(new CartToken("another-guest-cart-token")), Optional.empty());
    private static final IdempotencyKeys.StoredAnswer ANSWER = new IdempotencyKeys.StoredAnswer("c".repeat(64),
        UUID.randomUUID(), 201, Map.of("number", "1001", "totalCents", "4450000"));

    private final AsScopeOwner asOwner;
    private final TransactionTemplate newTransaction;
    private final JdbcTemplate owner;

    JdbcIdempotencyKeyStoreContractTest(JdbcIdempotencyKeyStore adapter, PlatformTransactionManager transactionManager,
                                        PostgreSQLContainer<?> postgres) {
        this.asOwner = new AsScopeOwner(adapter, new TransactionTemplate(transactionManager));
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsScopeOwner createSubject() {
        return asOwner;
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate idempotency_key");
    }

    @Test
    void givenAKeyClaimedByATransactionStillOpen_whenAnotherClaimsIt_thenItIsBusyUntilTheFirstEnds() {
        UUID key = UUID.randomUUID();

        var claims = asOwner.as(GUEST_ACTOR, () -> {
            var first = asOwner.adapter().claim(GUEST, key);
            var second = newTransaction.execute(status -> asOwner.adapter().claim(GUEST, key));
            var otherKey = newTransaction.execute(status -> asOwner.adapter().claim(GUEST, UUID.randomUUID()));
            return new IdempotencyKeys.Claim[] {first, second, otherKey};
        });

        assertThat(claims).containsExactly(new IdempotencyKeys.Claim.Fresh(), new IdempotencyKeys.Claim.Busy(),
            new IdempotencyKeys.Claim.Fresh());
        assertThat(asOwner.claim(GUEST, key)).isEqualTo(new IdempotencyKeys.Claim.Fresh());
    }

    @Test
    void givenNoTransaction_whenClaimingAKey_thenItIsRefused() {
        assertThatThrownBy(() -> CurrentActor.callAs(GUEST_ACTOR,
                () -> asOwner.adapter().claim(GUEST, UUID.randomUUID())))
            .hasMessageContaining("within the caller's transaction");
    }

    @Test
    void givenAGuestsKey_whenAnotherGuestLooksForItOrRecordsInItsScope_thenRowLevelSecurityKeepsItApart() {
        UUID key = UUID.randomUUID();
        asOwner.record(GUEST, key, ANSWER, NOW, NOW.plus(Duration.ofHours(24)));

        assertThat(asOwner.as(OTHER_GUEST, () -> asOwner.adapter().claim(GUEST, key)))
            .isEqualTo(new IdempotencyKeys.Claim.Fresh());
        assertThatThrownBy(() -> asOwner.as(OTHER_GUEST, () -> {
            asOwner.adapter().record(GUEST, UUID.randomUUID(), ANSWER, NOW, NOW.plus(Duration.ofHours(24)));
            return null;
        })).isInstanceOf(DataAccessException.class).hasStackTraceContaining("row-level security");
        assertThat(owner.queryForObject("select response_body ->> 'number' from idempotency_key", String.class))
            .isEqualTo("1001");
    }

    @Test
    void givenAnExpiredKey_whenTheAdministratorOrTheGuestPurges_thenRowLevelSecurityKeepsIt() {
        asOwner.record(GUEST, UUID.randomUUID(), ANSWER, NOW.minus(Duration.ofHours(25)), NOW.minusSeconds(1));

        assertThat(asOwner.as(RlsPolicyTester.ADMIN, () -> asOwner.adapter().deleteExpiredAt(NOW))).isZero();
        assertThat(asOwner.as(GUEST_ACTOR, () -> asOwner.adapter().deleteExpiredAt(NOW))).isZero();
        assertThat(owner.queryForObject("select count(*) from idempotency_key", Long.class)).isEqualTo(1);
    }

    /** Runs every call in its own committed transaction as the actor of the scope; the purge as the system. */
    record AsScopeOwner(JdbcIdempotencyKeyStore adapter, TransactionTemplate transactions)
        implements IdempotencyKeys, ExpiredIdempotencyKeys {

        @Override
        public Claim claim(IdempotencyScope scope, UUID key) {
            return as(actorOf(scope), () -> adapter.claim(scope, key));
        }

        @Override
        public void record(IdempotencyScope scope, UUID key, StoredAnswer answer, Instant createdAt,
                           Instant expiresAt) {
            as(actorOf(scope), () -> {
                adapter.record(scope, key, answer, createdAt, expiresAt);
                return null;
            });
        }

        @Override
        public int deleteExpiredAt(Instant now) {
            return as(Actor.system(), () -> adapter.deleteExpiredAt(now));
        }

        <R> R as(Actor actor, Supplier<R> work) {
            return CurrentActor.callAs(actor, () -> transactions.execute(status -> work.get()));
        }

        private static Actor actorOf(IdempotencyScope scope) {
            return scope.equals(CUSTOMER) ? CUSTOMER_ACTOR : GUEST_ACTOR;
        }
    }
}
