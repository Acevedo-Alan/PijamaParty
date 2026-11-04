package com.pijamaparty.store.ordering.adapter.out.persistence;

import com.pijamaparty.store.ordering.application.OrderCountsByCustomer;
import java.sql.Types;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * UC-34 (T-E8-10): the orders linked to each customer ({@code orders.user_id}, over {@code orders_user_created_idx},
 * V14), in the caller's transaction. A guest's order has no user and never counts. Constant SQL (RNF-28).
 */
@Repository
class JdbcOrderCountsByCustomer implements OrderCountsByCustomer {

    private static final String COUNTS = """
        SELECT user_id, count(*) AS orders
        FROM orders
        WHERE user_id = ANY (CAST(:ids AS uuid[]))
        GROUP BY user_id
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
}
