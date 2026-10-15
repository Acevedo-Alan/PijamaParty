package com.pijamaparty.store.ordering.adapter.out.persistence;

import com.pijamaparty.store.ordering.api.WithdrawalNotices.WithdrawalNotice;
import com.pijamaparty.store.ordering.application.ActiveWithdrawals;
import com.pijamaparty.store.ordering.application.SaveWithdrawals;
import com.pijamaparty.store.ordering.application.WithdrawableOrders;
import com.pijamaparty.store.ordering.application.WithdrawalNoticeDetails;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.WithdrawalCode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Withdrawal requests in table {@code withdrawal_request} (V25), and the orders they are for, in the caller's
 * transaction, as {@code SYSTEM} (RNF-27).
 */
@Repository
class JdbcWithdrawalStore implements WithdrawableOrders, ActiveWithdrawals, SaveWithdrawals, WithdrawalNoticeDetails {

    // UC-29: the number is unique (orders_number_uk); the lock holds a second request for the order until this one
    // commits.
    private static final String LOCK_BY_NUMBER = """
        SELECT id, number, customer_email, status, created_at, delivered_at FROM orders
        WHERE number = :number
        FOR UPDATE
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
        SELECT w.id, w.code, w.out_of_term, o.number, o.customer_name, o.customer_email
        FROM withdrawal_request w JOIN orders o ON o.id = w.order_id
        WHERE w.id = :id
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcWithdrawalStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<WithdrawableOrder> lock(long number) {
        return jdbc.query(LOCK_BY_NUMBER, new MapSqlParameterSource("number", number), (row, n) ->
                new WithdrawableOrder(OrderId.of(row.getObject("id", UUID.class)), row.getLong("number"),
                    row.getString("customer_email"), OrderStatus.valueOf(row.getString("status")),
                    row.getObject("created_at", OffsetDateTime.class).toInstant(),
                    Optional.ofNullable(row.getObject("delivered_at", OffsetDateTime.class))
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
                    row.getString("customer_name"), row.getString("customer_email"),
                    row.getBoolean("out_of_term")))
            .stream().findFirst();
    }
}
