package com.pijamaparty.store.ordering.adapter.out.persistence;

import com.pijamaparty.store.ordering.application.OrderTransitions;
import com.pijamaparty.store.ordering.application.OverdueOrders;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Orders in table {@code orders} (V14), in the caller's transaction. Row Level Security decides which orders the
 * actor reaches (RNF-27).
 */
@Repository
class JdbcOrderStore implements OverdueOrders, OrderTransitions {

    // UC-16: oldest expiry first, over the partial index orders_reservation_expiry_idx. SKIP LOCKED lets two runs work
    // side by side without expiring the same order.
    private static final String LOCK_NEXT_DUE = """
        SELECT id FROM orders
        WHERE status = 'AWAITING_PAYMENT' AND reservation_expires_at <= :now
        ORDER BY reservation_expires_at, id
        LIMIT 1
        FOR UPDATE SKIP LOCKED
        """;

    // RNF-20.5: conditional, so a transition that lost the race affects no row.
    private static final String TRANSITION = """
        UPDATE orders SET status = :to, updated_at = :at
        WHERE id = :id AND status = :from
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcOrderStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<OrderId> lockNextDue(Instant now) {
        return jdbc.queryForList(LOCK_NEXT_DUE, new MapSqlParameterSource("now", now.atOffset(ZoneOffset.UTC)),
            UUID.class).stream().findFirst().map(OrderId::of);
    }

    @Override
    public boolean transition(OrderId order, OrderStatus from, OrderStatus to, Instant at) {
        if (!from.canBecome(to)) {
            throw new IllegalArgumentException("an order cannot go from " + from + " to " + to);
        }
        return jdbc.update(TRANSITION, new MapSqlParameterSource("id", order.value())
            .addValue("from", from.name())
            .addValue("to", to.name())
            .addValue("at", at.atOffset(ZoneOffset.UTC))) == 1;
    }
}
