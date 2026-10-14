package com.pijamaparty.store.payments.adapter.out.persistence;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.PaymentNotifications;
import com.pijamaparty.store.payments.domain.VerifiedPayment;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notifications in table {@code payment_event} (V22), in the caller's transaction. Row Level Security lets only the
 * system write (RNF-27).
 */
@Repository
class JdbcPaymentNotificationStore implements PaymentNotifications {

    // RNF-20.3: over payment_event_pk. A concurrent insert of the same key waits for the other transaction and inserts
    // nothing once it commits, so no row comes back.
    private static final String INSERT_FIRST = """
        INSERT INTO payment_event (provider, external_payment_id, status, order_number, received_at)
        VALUES (:provider, :externalPaymentId, :status, :orderNumber, :receivedAt)
        ON CONFLICT (provider, external_payment_id, status) DO NOTHING
        RETURNING external_payment_id
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcPaymentNotificationStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordFirst(PaymentProvider provider, VerifiedPayment verified, Instant receivedAt) {
        return !jdbc.queryForList(INSERT_FIRST, new MapSqlParameterSource("provider", provider.name())
                .addValue("externalPaymentId", verified.externalId())
                .addValue("status", verified.status().name())
                .addValue("orderNumber", verified.orderNumber())
                .addValue("receivedAt", receivedAt.atOffset(ZoneOffset.UTC)), String.class)
            .isEmpty();
    }
}
