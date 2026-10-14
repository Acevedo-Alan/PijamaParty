package com.pijamaparty.store.platform.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** T-E3-14 (RNF-20.2): the daily job deletes, as the system and past Row Level Security, the keys past their expiry. */
@ApiTest
class IdempotencyKeyPurgeTest {

    private final IdempotencyKeyPurge purge;
    private final JdbcTemplate owner;

    IdempotencyKeyPurgeTest(IdempotencyKeyPurge purge, PostgreSQLContainer<?> postgres) {
        this.purge = purge;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate idempotency_key");
    }

    @Test
    void givenACustomersAndAGuestsKeysPastTheirExpiry_whenTheJobRuns_thenOnlyTheyAreDeleted() {
        key("USER:" + UUID.randomUUID(), "25 hours", "1 hour");
        key("CART:" + new CartToken("a-guest-cart-token").hash(), "30 hours", "6 hours");
        UUID alive = key("CART:" + new CartToken("another-guest-cart-token").hash(), "1 hour", "-23 hours");

        purge.run();

        assertThat(owner.queryForList("select idempotency_key from idempotency_key", UUID.class))
            .containsExactly(alive);
    }

    @Test
    void givenTheJobRuns_whenItEnds_thenNoActorIsLeftBoundToTheThread() {
        purge.run();

        assertThat(CurrentActor.get()).isEmpty();
    }

    /** A key of {@code scope} created {@code age} ago that expired {@code expiredFor} ago (negative: still alive). */
    private UUID key(String scope, String age, String expiredFor) {
        UUID key = UUID.randomUUID();
        owner.update("""
            insert into idempotency_key (scope, idempotency_key, request_hash, resource_id, response_status,
                                         response_body, created_at, expires_at)
            values (?, ?, ?, ?, 201, '{}', now() - cast(? as interval), now() - cast(? as interval))
            """, scope, key, "d".repeat(64), UUID.randomUUID(), age, expiredFor);
        return key;
    }
}
