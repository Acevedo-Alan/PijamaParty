package com.pijamaparty.store.cart.adapter.in.scheduler;

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

/** T-E2-04: the daily job deletes, as the system and past Row Level Security, the carts idle for more than 30 days. */
@ApiTest
class IdleCartCleanupTest {

    private final IdleCartCleanup cleanup;
    private final JdbcTemplate owner;

    IdleCartCleanupTest(IdleCartCleanup cleanup, PostgreSQLContainer<?> postgres) {
        this.cleanup = cleanup;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate cart_item, cart");
    }

    @Test
    void givenGuestAndCustomerCartsIdleForMoreThan30Days_whenTheJobRuns_thenOnlyTheyAreDeletedWithTheirItems() {
        cart(guestTokenHash(), null, "31 days");
        cart(null, UUID.randomUUID(), "45 days");
        UUID activeCart = cart(guestTokenHash(), null, "29 days");

        cleanup.run();

        assertThat(owner.queryForList("select id from cart", UUID.class)).containsExactly(activeCart);
        assertThat(owner.queryForList("select cart_id from cart_item", UUID.class)).containsExactly(activeCart);
    }

    @Test
    void givenTheJobRuns_whenItEnds_thenNoActorIsLeftBoundToTheThread() {
        cleanup.run();

        assertThat(CurrentActor.get()).isEmpty();
    }

    /** A guest's or a customer's cart with one item, last changed {@code idleFor} ago. */
    private UUID cart(String tokenHash, UUID userId, String idleFor) {
        UUID id = UUID.randomUUID();
        owner.update("""
            insert into cart (id, token_hash, user_id, created_at, updated_at)
            values (?, ?, ?, now() - cast(? as interval), now() - cast(? as interval))
            """, id, tokenHash, userId, idleFor, idleFor);
        owner.update("""
            insert into cart_item (id, cart_id, variant_id, quantity, last_seen_price_cents, created_at)
            values (?, ?, ?, 1, 1500000, now())
            """, UUID.randomUUID(), id, UUID.randomUUID());
        return id;
    }

    private static String guestTokenHash() {
        return new CartToken(UUID.randomUUID().toString()).hash();
    }
}
