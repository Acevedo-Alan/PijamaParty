package com.pijamaparty.store.ordering.adapter.out.persistence;

import com.pijamaparty.store.ordering.api.OrderWithdrawals.ActiveWithdrawal;
import com.pijamaparty.store.ordering.api.WithdrawalNotices.WithdrawalNotice;
import com.pijamaparty.store.ordering.application.ActiveWithdrawalViews;
import com.pijamaparty.store.ordering.application.ActiveWithdrawals;
import com.pijamaparty.store.ordering.application.SaveWithdrawals;
import com.pijamaparty.store.ordering.application.WithdrawableOrders;
import com.pijamaparty.store.ordering.application.WithdrawalNoticeDetails;
import com.pijamaparty.store.ordering.application.WithdrawalPages;
import com.pijamaparty.store.ordering.application.WithdrawalResolutions;
import com.pijamaparty.store.ordering.application.WithdrawalSummary;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.WithdrawalCode;
import com.pijamaparty.store.ordering.domain.WithdrawalStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Withdrawal requests in table {@code withdrawal_request} (V25, V26), and the orders they are for, in the caller's
 * transaction. The form runs as the guest or the customer that sends it, which reaches an order's requests only after
 * {@link #lock} matched its number and email in the same transaction; the email job reads the notices
 * as {@code SYSTEM}; the back office lists and resolves them as {@code ADMIN} (RNF-27).
 */
@Repository
class JdbcWithdrawalStore implements WithdrawableOrders, ActiveWithdrawals, SaveWithdrawals, WithdrawalNoticeDetails,
    WithdrawalPages, ActiveWithdrawalViews, WithdrawalResolutions {

    // UC-29 (V26): only an order of this number and email, locked, which the transaction may then reach
    // (app_withdrawal_order_id). The lock holds a second request for the order until this one commits.
    private static final String LOCK_MATCHING = """
        SELECT order_id, order_number, order_status, order_created_at, order_delivered_at
        FROM lock_withdrawable_order(:number, :email)
        """;

    private static final String ACTIVE_CODE = """
        SELECT code FROM withdrawal_request
        WHERE order_id = :order AND status = 'RECEIVED'
        """;

    // A code already taken inserts nothing (withdrawal_request_code_uk). A second active request of the order would
    // break withdrawal_request_active_order_uk instead: the caller holds the order locked, so it cannot happen.
    private static final String INSERT = """
        INSERT INTO withdrawal_request (id, order_id, code, status, out_of_term, created_at)
        VALUES (:id, :order, :code, 'RECEIVED', :outOfTerm, :createdAt)
        ON CONFLICT (code) DO NOTHING
        """;

    private static final String NOTICE = """
        SELECT w.id, w.code, w.out_of_term, o.number, o.customer_first_name, o.customer_last_name,
               o.customer_email
        FROM withdrawal_request w JOIN orders o ON o.id = w.order_id
        WHERE w.id = :id
        """;

    // T-E3-26: the back-office list. A small table (fewer than 20 orders a day), so no index of its own.
    private static final String PAGE_ALL = """
        SELECT w.id, w.code, w.order_id, o.number, o.status AS order_status,
               o.customer_first_name || ' ' || o.customer_last_name AS customer_full_name, w.created_at,
               w.out_of_term, w.status, w.resolved_at
        FROM withdrawal_request w JOIN orders o ON o.id = w.order_id
        ORDER BY w.created_at DESC, w.code
        LIMIT :limit OFFSET :offset
        """;

    private static final String PAGE_BY_STATUS = """
        SELECT w.id, w.code, w.order_id, o.number, o.status AS order_status,
               o.customer_first_name || ' ' || o.customer_last_name AS customer_full_name, w.created_at,
               w.out_of_term, w.status, w.resolved_at
        FROM withdrawal_request w JOIN orders o ON o.id = w.order_id
        WHERE w.status = :status
        ORDER BY w.created_at DESC, w.code
        LIMIT :limit OFFSET :offset
        """;

    private static final String ACTIVE_VIEW = """
        SELECT id, code, out_of_term, created_at FROM withdrawal_request
        WHERE order_id = :order AND status = 'RECEIVED'
        """;

    // T-E3-26: two administrators resolving the same request run one after the other.
    private static final String LOCK_FOR_RESOLUTION = """
        SELECT code, status, created_at, resolved_at FROM withdrawal_request
        WHERE id = :id
        FOR UPDATE
        """;

    private static final String RESOLVE = """
        UPDATE withdrawal_request SET status = 'RESOLVED', resolved_at = :at
        WHERE id = :id AND status = 'RECEIVED'
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcWithdrawalStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<WithdrawableOrder> lock(long number, String email) {
        return jdbc.query(LOCK_MATCHING, new MapSqlParameterSource("number", number).addValue("email", email),
                (row, n) -> new WithdrawableOrder(OrderId.of(row.getObject("order_id", UUID.class)),
                    row.getLong("order_number"), OrderStatus.valueOf(row.getString("order_status")),
                    row.getObject("order_created_at", OffsetDateTime.class).toInstant(),
                    Optional.ofNullable(row.getObject("order_delivered_at", OffsetDateTime.class))
                        .map(OffsetDateTime::toInstant)))
            .stream().findFirst();
    }

    @Override
    public Optional<WithdrawalCode> codeOf(OrderId order) {
        return jdbc.queryForList(ACTIVE_CODE, new MapSqlParameterSource("order", order.value()), String.class)
            .stream().findFirst().map(WithdrawalCode::new);
    }

    @Override
    public boolean insert(NewWithdrawal withdrawal) {
        return jdbc.update(INSERT, new MapSqlParameterSource("id", withdrawal.id())
            .addValue("order", withdrawal.order().value())
            .addValue("code", withdrawal.code().value())
            .addValue("outOfTerm", withdrawal.outOfTerm())
            .addValue("createdAt", withdrawal.createdAt().atOffset(ZoneOffset.UTC))) == 1;
    }

    @Override
    public Optional<WithdrawalNotice> noticeOf(UUID requestId) {
        return jdbc.query(NOTICE, new MapSqlParameterSource("id", requestId), (row, n) ->
                new WithdrawalNotice(row.getObject("id", UUID.class), row.getString("code"), row.getLong("number"),
                    row.getString("customer_first_name"), row.getString("customer_last_name"),
                    row.getString("customer_email"), row.getBoolean("out_of_term")))
            .stream().findFirst();
    }

    @Override
    public List<WithdrawalSummary> page(Optional<WithdrawalStatus> status, int offset, int limit) {
        var params = new MapSqlParameterSource("offset", offset).addValue("limit", limit);
        status.ifPresent(wanted -> params.addValue("status", wanted.name()));
        return status.isPresent()
            ? jdbc.query(PAGE_BY_STATUS, params, JdbcWithdrawalStore::summary)
            : jdbc.query(PAGE_ALL, params, JdbcWithdrawalStore::summary);
    }

    private static WithdrawalSummary summary(ResultSet row, int rowNumber) throws SQLException {
        return new WithdrawalSummary(
            row.getObject("id", UUID.class), new WithdrawalCode(row.getString("code")),
            OrderId.of(row.getObject("order_id", UUID.class)), row.getLong("number"),
            OrderStatus.valueOf(row.getString("order_status")), row.getString("customer_full_name"),
            row.getObject("created_at", OffsetDateTime.class).toInstant(), row.getBoolean("out_of_term"),
            WithdrawalStatus.valueOf(row.getString("status")),
            Optional.ofNullable(row.getObject("resolved_at", OffsetDateTime.class)).map(OffsetDateTime::toInstant));
    }

    @Override
    public Optional<ActiveWithdrawal> activeViewOf(OrderId order) {
        return jdbc.query(ACTIVE_VIEW, new MapSqlParameterSource("order", order.value()), (row, n) ->
                new ActiveWithdrawal(row.getObject("id", UUID.class), row.getString("code"),
                    row.getBoolean("out_of_term"), row.getObject("created_at", OffsetDateTime.class).toInstant()))
            .stream().findFirst();
    }

    @Override
    public Optional<LockedWithdrawal> lockForResolution(UUID request) {
        return jdbc.query(LOCK_FOR_RESOLUTION, new MapSqlParameterSource("id", request), (row, n) ->
                new LockedWithdrawal(new WithdrawalCode(row.getString("code")),
                    WithdrawalStatus.valueOf(row.getString("status")),
                    row.getObject("created_at", OffsetDateTime.class).toInstant(),
                    Optional.ofNullable(row.getObject("resolved_at", OffsetDateTime.class))
                        .map(OffsetDateTime::toInstant)))
            .stream().findFirst();
    }

    @Override
    public void resolve(UUID request, Instant at) {
        int updated = jdbc.update(RESOLVE, new MapSqlParameterSource("id", request)
            .addValue("at", at.atOffset(ZoneOffset.UTC)));
        if (updated != 1) {
            throw new IllegalStateException("withdrawal request " + request + " is not a locked received one");
        }
    }
}
