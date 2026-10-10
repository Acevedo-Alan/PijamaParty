package com.pijamaparty.store.inventory.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** T-E1-01: the inventory schema (V4) enforces UC-07 and RNF-20.4, connected as the application role. */
@ApiTest
class InventorySchemaTest {

    private static final String RECORD_ORDER_MOVEMENT = """
        insert into stock_movement (id, variant_id, type, quantity, order_id, created_at)
        values (?, ?, 'RESERVATION', -1, ?, now())
        on conflict do nothing
        """;

    private final JdbcTemplate app;
    private final JdbcTemplate owner;

    InventorySchemaTest(JdbcTemplate jdbc, PostgreSQLContainer<?> postgres) {
        this.app = jdbc;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate stock_movement, stock_level");
    }

    @Test
    void givenAStockOf3_whenSubtracting5_thenItIsRejected() {
        var variant = stockLevel(3);

        assertThatThrownBy(() -> app.update("update stock_level set on_hand = on_hand - 5 where variant_id = ?", variant))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("stock_level_on_hand_ck");
    }

    @Test
    void givenAnAdjustment_whenRecorded_thenTheHistoryKeepsDateUserQuantityAndNote() {
        var variant = stockLevel(3);
        var admin = UUID.randomUUID();
        app.update("""
            insert into stock_movement (id, variant_id, type, quantity, note, user_id, created_at)
            values (?, ?, 'ADJUSTMENT', 2, 'Reposición', ?, now())
            """, UUID.randomUUID(), variant, admin);

        var row = app.queryForMap("select created_at, user_id, quantity, note from stock_movement where variant_id = ?",
            variant);
        assertThat(row).containsEntry("user_id", admin).containsEntry("quantity", 2).containsEntry("note", "Reposición")
            .containsKey("created_at");
    }

    @Test
    void givenANoteLongerThan200Characters_whenRecorded_thenItIsRejected() {
        var variant = stockLevel(3);

        assertThatThrownBy(() -> app.update("""
                insert into stock_movement (id, variant_id, type, quantity, note, user_id, created_at)
                values (?, ?, 'ADJUSTMENT', 2, ?, ?, now())
                """, UUID.randomUUID(), variant, "x".repeat(201), UUID.randomUUID()))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("stock_movement_note_length_ck");
    }

    @Test
    void givenAnAdjustmentWithoutUser_whenRecorded_thenItIsRejected() {
        var variant = stockLevel(3);

        assertThatThrownBy(() -> app.update("""
                insert into stock_movement (id, variant_id, type, quantity, created_at)
                values (?, ?, 'ADJUSTMENT', 2, now())
                """, UUID.randomUUID(), variant))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("stock_movement_user_ck");
    }

    @Test
    void givenARecordedMovement_whenTheApplicationTriesToChangeOrDeleteIt_thenItIsDenied() {
        var variant = stockLevel(3);
        app.update(RECORD_ORDER_MOVEMENT, UUID.randomUUID(), variant, UUID.randomUUID());

        assertThatThrownBy(() -> app.update("update stock_movement set quantity = 5"))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
        assertThatThrownBy(() -> app.update("delete from stock_movement"))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
    }

    @Test
    void givenAnOrderMovement_whenItIsRecordedAgain_thenTheRepeatIsANoOp() {
        var variant = stockLevel(3);
        var order = UUID.randomUUID();

        assertThat(app.update(RECORD_ORDER_MOVEMENT, UUID.randomUUID(), variant, order)).isOne();
        assertThat(app.update(RECORD_ORDER_MOVEMENT, UUID.randomUUID(), variant, order)).isZero();
        assertThat(movementsOf(variant)).isOne();
    }

    @Test
    void givenAnOrderMovement_whenTwoThreadsRecordItAtOnce_thenOnlyOneIsStored() throws Exception {
        var variant = stockLevel(3);
        var order = UUID.randomUUID();
        var start = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            var first = CompletableFuture.supplyAsync(() -> recordAfter(start, variant, order), threads);
            var second = CompletableFuture.supplyAsync(() -> recordAfter(start, variant, order), threads);
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS)).isOne();
            assertThat(movementsOf(variant)).isOne();
        } finally {
            threads.shutdownNow();
        }
    }

    private int recordAfter(CountDownLatch start, UUID variant, UUID order) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return app.update(RECORD_ORDER_MOVEMENT, UUID.randomUUID(), variant, order);
    }

    private UUID stockLevel(int onHand) {
        var variant = UUID.randomUUID();
        app.update("insert into stock_level (variant_id, on_hand, updated_at) values (?, ?, now())", variant, onHand);
        return variant;
    }

    private Integer movementsOf(UUID variant) {
        return app.queryForObject("select count(*) from stock_movement where variant_id = ?", Integer.class, variant);
    }
}
