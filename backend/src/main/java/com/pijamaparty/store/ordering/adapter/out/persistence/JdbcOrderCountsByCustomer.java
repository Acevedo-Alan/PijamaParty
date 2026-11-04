package com.pijamaparty.store.ordering.adapter.out.persistence;

import com.pijamaparty.store.ordering.application.CustomerPurchaseFigures;
import com.pijamaparty.store.ordering.application.OrderCountsByCustomer;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.domain.UserId;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * UC-34 (T-E8-10, T-E8-11): the orders linked to each customer ({@code orders.user_id}, over
 * {@code orders_user_created_idx}, V14), in the caller's transaction. A guest's order has no user and never counts.
 * Constant SQL (RNF-28); the statuses that count as purchases come from {@link OrderStatus} as an array parameter.
 */
@Repository
class JdbcOrderCountsByCustomer implements OrderCountsByCustomer, CustomerPurchaseFigures {

    private static final String[] PURCHASE_STATUSES = Arrays.stream(OrderStatus.values())
        .filter(OrderStatus::isBought).map(OrderStatus::name).toArray(String[]::new);

    private static final String COUNTS = """
        SELECT user_id, count(*) AS orders
        FROM orders
        WHERE user_id = ANY (CAST(:ids AS uuid[]))
        GROUP BY user_id
        """;

    private static final String FIGURES = """
        SELECT count(*) AS orders,
               count(*) FILTER (WHERE status = ANY (CAST(:statuses AS text[]))) AS purchases,
               coalesce(sum(total_cents) FILTER (WHERE status = ANY (CAST(:statuses AS text[]))), 0) AS spent,
               max(created_at) FILTER (WHERE status = ANY (CAST(:statuses AS text[]))) AS last_purchase
        FROM orders
        WHERE user_id = :userId
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcOrderCountsByCustomer(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, Long> countsOf(Collection<UUID> customerIds) {
        var params = new MapSqlParameterSource()
            .addValue("ids", customerIds.stream().distinct().toArray(UUID[]::new), Types.ARRAY);
        var counts = new HashMap<UUID, Long>();
        jdbc.query(COUNTS, params, row -> {
            counts.put(row.getObject("user_id", UUID.class), row.getLong("orders"));
        });
        return Map.copyOf(counts);
    }

    @Override
    public Figures figuresOf(UserId customer) {
        var params = new MapSqlParameterSource("userId", customer.value())
            .addValue("statuses", PURCHASE_STATUSES, Types.ARRAY);
        return jdbc.queryForObject(FIGURES, params, (row, n) -> new Figures(row.getLong("orders"),
            row.getLong("purchases"), Money.of(row.getLong("spent")),
            Optional.ofNullable(row.getObject("last_purchase", OffsetDateTime.class)).map(OffsetDateTime::toInstant)));
    }
}
