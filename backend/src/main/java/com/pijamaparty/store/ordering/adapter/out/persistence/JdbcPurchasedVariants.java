package com.pijamaparty.store.ordering.adapter.out.persistence;

import com.pijamaparty.store.ordering.application.PurchasedVariants;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.UserId;
import java.sql.Types;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * UC-24: a customer's orders by their items in tables {@code orders} and {@code order_item} (V14), in the caller's
 * transaction. Filtering by the customer too keeps the order of a tracking token the actor holds out
 * ({@code orders_tracking_select}).
 */
@Repository
class JdbcPurchasedVariants implements PurchasedVariants {

    // Over orders_user_created_idx, then each order's items by order_item_order_variant_uk.
    private static final String HAS_ORDER_WITH_ANY = """
        SELECT EXISTS (
            SELECT 1
            FROM orders o
            JOIN order_item i ON i.order_id = o.id
            WHERE o.user_id = :customer
              AND o.status = ANY (CAST(:statuses AS text[]))
              AND i.variant_id = ANY (CAST(:variantIds AS uuid[])))
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcPurchasedVariants(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean hasOrderWithAny(UserId customer, Set<OrderStatus> statuses, Collection<UUID> variantIds) {
        if (statuses.isEmpty() || variantIds.isEmpty()) {
            return false;
        }
        return Boolean.TRUE.equals(jdbc.queryForObject(HAS_ORDER_WITH_ANY, new MapSqlParameterSource()
            .addValue("customer", customer.value())
            .addValue("statuses", statuses.stream().map(Enum::name).toArray(String[]::new), Types.ARRAY)
            .addValue("variantIds", variantIds.toArray(UUID[]::new), Types.ARRAY), Boolean.class));
    }
}
