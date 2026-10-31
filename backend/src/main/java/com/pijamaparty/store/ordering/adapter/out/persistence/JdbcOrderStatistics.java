package com.pijamaparty.store.ordering.adapter.out.persistence;

import com.pijamaparty.store.ordering.api.AdminOrderDetails.Status;
import com.pijamaparty.store.ordering.api.OrderWorkload.RecentOrder;
import com.pijamaparty.store.ordering.api.SalesFigures.DayTotal;
import com.pijamaparty.store.ordering.api.SalesFigures.MonthTotal;
import com.pijamaparty.store.ordering.api.SalesFigures.Total;
import com.pijamaparty.store.ordering.api.SalesFigures.VariantSales;
import com.pijamaparty.store.ordering.application.SalesStatistics;
import com.pijamaparty.store.ordering.application.WorkloadStatistics;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.WithdrawalStatus;
import com.pijamaparty.store.platform.domain.Money;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * UC-33: the summary's reads of orders and withdrawal requests, as the caller (the administrator, whom Row Level
 * Security lets read them all). Each query is one constant text block (RNF-28); the statuses come from
 * {@link OrderStatus} as array parameters, and the dates are Argentina dates (R-07).
 */
@Repository
class JdbcOrderStatistics implements SalesStatistics, WorkloadStatistics {

    private static final String[] SALE_STATUSES = Arrays.stream(OrderStatus.values())
        .filter(OrderStatus::isBought).map(OrderStatus::name).toArray(String[]::new);
    private static final String[] OPEN_STATUSES = Arrays.stream(OrderStatus.values())
        .filter(OrderStatus::isOpen).map(OrderStatus::name).toArray(String[]::new);

    private static final String TOTALS = """
        SELECT coalesce(sum(total_cents), 0) AS amount, count(*) AS orders
        FROM orders
        WHERE status = ANY (CAST(:statuses AS text[])) AND created_at >= :from AND created_at < :to
        """;

    private static final String PER_DAY = """
        SELECT (created_at AT TIME ZONE 'America/Argentina/Buenos_Aires')::date AS day,
               sum(total_cents) AS amount, count(*) AS orders
        FROM orders
        WHERE status = ANY (CAST(:statuses AS text[])) AND created_at >= :from AND created_at < :to
        GROUP BY 1
        ORDER BY 1
        """;

    private static final String PER_MONTH = """
        SELECT date_trunc('month', created_at AT TIME ZONE 'America/Argentina/Buenos_Aires')::date AS month,
               sum(total_cents) AS amount, count(*) AS orders
        FROM orders
        WHERE status = ANY (CAST(:statuses AS text[])) AND created_at >= :from AND created_at < :to
        GROUP BY 1
        ORDER BY 1
        """;

    private static final String BY_VARIANT = """
        SELECT i.variant_id, sum(i.quantity) AS units, sum(i.quantity * i.unit_price_cents) AS amount
        FROM order_item i
        JOIN orders o ON o.id = i.order_id
        WHERE o.status = ANY (CAST(:statuses AS text[])) AND o.created_at >= :from AND o.created_at < :to
        GROUP BY i.variant_id
        """;

    private static final String RECEIVED_WITHDRAWALS = """
        SELECT count(*) FROM withdrawal_request WHERE status = :status
        """;

    private static final String NEWEST = """
        SELECT id, number, created_at, status, customer_first_name || ' ' || customer_last_name AS buyer_name,
               total_cents
        FROM orders
        ORDER BY created_at DESC, number DESC
        LIMIT :limit
        """;

    private static final String OPEN_AMONG = """
        SELECT id FROM orders
        WHERE id = ANY (CAST(:ids AS uuid[])) AND status = ANY (CAST(:statuses AS text[]))
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcOrderStatistics(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static MapSqlParameterSource sales(Instant from, Instant to) {
        return new MapSqlParameterSource().addValue("statuses", SALE_STATUSES, Types.ARRAY)
            .addValue("from", from.atOffset(ZoneOffset.UTC))
            .addValue("to", to.atOffset(ZoneOffset.UTC));
    }

    @Override
    public Total totals(Instant from, Instant to) {
        return jdbc.queryForObject(TOTALS, sales(from, to),
            (row, n) -> new Total(Money.of(row.getLong("amount")), row.getLong("orders")));
    }

    @Override
    public List<DayTotal> perDay(Instant from, Instant to) {
        return jdbc.query(PER_DAY, sales(from, to), (row, n) -> new DayTotal(row.getObject("day", LocalDate.class),
            Money.of(row.getLong("amount")), row.getLong("orders")));
    }

    @Override
    public List<MonthTotal> perMonth(Instant from, Instant to) {
        return jdbc.query(PER_MONTH, sales(from, to), (row, n) -> new MonthTotal(
            YearMonth.from(row.getObject("month", LocalDate.class)), Money.of(row.getLong("amount")),
            row.getLong("orders")));
    }

    @Override
    public List<VariantSales> byVariant(Instant from, Instant to) {
        return jdbc.query(BY_VARIANT, sales(from, to), (row, n) -> new VariantSales(
            row.getObject("variant_id", UUID.class), row.getLong("units"), Money.of(row.getLong("amount"))));
    }

    @Override
    public long receivedWithdrawals() {
        Long count = jdbc.queryForObject(RECEIVED_WITHDRAWALS,
            new MapSqlParameterSource("status", WithdrawalStatus.RECEIVED.name()), Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<RecentOrder> newest(int limit) {
        return jdbc.query(NEWEST, new MapSqlParameterSource("limit", limit), (row, n) -> new RecentOrder(
            row.getObject("id", UUID.class), row.getLong("number"),
            row.getObject("created_at", OffsetDateTime.class).toInstant(), row.getString("buyer_name"),
            Status.valueOf(row.getString("status")), Money.of(row.getLong("total_cents"))));
    }

    @Override
    public Set<UUID> openAmong(Collection<UUID> orderIds) {
        var params = new MapSqlParameterSource()
            .addValue("ids", orderIds.stream().distinct().toArray(UUID[]::new), Types.ARRAY)
            .addValue("statuses", OPEN_STATUSES, Types.ARRAY);
        return Set.copyOf(new HashSet<>(jdbc.queryForList(OPEN_AMONG, params, UUID.class)));
    }
}
