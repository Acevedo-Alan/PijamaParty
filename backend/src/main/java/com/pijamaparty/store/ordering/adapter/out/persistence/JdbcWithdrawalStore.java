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
 * Withdrawal requests in table {@code withdrawal_request} (V25, V26), and the orders they are for, in the caller's
 * transaction. The form runs as the guest or the customer that sends it, which reaches an order's requests only after
 * {@link #lock} matched its number and email in the same transaction; the email job reads the notices as
 * {@code SYSTEM} (RNF-27).
 */
@Repository
class JdbcWithdrawalStore implements WithdrawableOrders, ActiveWithdrawals, SaveWithdrawals, WithdrawalNoticeDetails {

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
        SELECT w.id, w.code, w.out_of_term, o.number, o.customer_name, o.customer_email
        FROM withdrawal_request w JOIN orders o ON o.id = w.order_id
        WHERE w.id = :id
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
                    row.getString("customer_name"), row.getString("customer_email"),
                    row.getBoolean("out_of_term")))
            .stream().findFirst();
    }
}
