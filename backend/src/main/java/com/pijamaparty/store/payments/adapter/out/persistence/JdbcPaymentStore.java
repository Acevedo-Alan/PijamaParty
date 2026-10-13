package com.pijamaparty.store.payments.adapter.out.persistence;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.LoadPayments;
import com.pijamaparty.store.payments.application.PaymentConfirmations;
import com.pijamaparty.store.payments.application.PaymentsToConfirm;
import com.pijamaparty.store.payments.application.SavePayments;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.payments.domain.PaymentStatus;
import com.pijamaparty.store.platform.domain.Money;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payments in table {@code payment} (V15, V18). Opening a payment runs each call in its own transaction (or joins the
 * caller's), so the payment use case commits before calling the provider (RNF-21); confirming one locks and records it
 * in the caller's transaction. Row Level Security lets only the system write (RNF-27).
 */
@Repository
class JdbcPaymentStore implements LoadPayments, SavePayments, PaymentsToConfirm, PaymentConfirmations {

    private static final String SELECT_CHECKOUT = """
        SELECT checkout_id, checkout_url FROM payment
        WHERE order_id = :orderId AND provider = :provider AND checkout_id IS NOT NULL
        """;

    // RNF-20.8: one payment per order and provider (payment_order_provider_uk); a concurrent insert that loses does
    // nothing, and the winner's row is the payment.
    private static final String INSERT_PENDING = """
        INSERT INTO payment (id, order_id, order_number, provider, amount_cents, status, created_at, updated_at)
        VALUES (:id, :orderId, :orderNumber, :provider, :amountCents, 'PENDING', :createdAt, :createdAt)
        ON CONFLICT (order_id, provider) DO NOTHING
        """;

    // Conditional, so the first checkout recorded stays the payment's.
    private static final String RECORD_CHECKOUT = """
        UPDATE payment SET checkout_id = :checkoutId, checkout_url = :checkoutUrl, updated_at = :at
        WHERE order_id = :orderId AND provider = :provider AND checkout_id IS NULL
        """;

    // UC-15: over payment_provider_order_number_uk. The lock makes two notifications of the payment wait for each
    // other (RNF-20.3).
    private static final String LOCK_BY_ORDER_NUMBER = """
        SELECT order_id, order_number, status, amount_cents FROM payment
        WHERE provider = :provider AND order_number = :orderNumber
        FOR UPDATE
        """;

    // RNF-20.5: an approved payment is final.
    private static final String RECORD_VERIFICATION = """
        UPDATE payment SET status = :status, external_payment_id = :externalPaymentId, updated_at = :at
        WHERE order_id = :orderId AND provider = :provider AND status <> 'APPROVED'
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcPaymentStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Checkout> checkoutOf(UUID orderId, PaymentProvider provider) {
        return jdbc.query(SELECT_CHECKOUT, keyOf(orderId, provider),
                (row, n) -> new Checkout(row.getString("checkout_id"), URI.create(row.getString("checkout_url"))))
            .stream().findFirst();
    }

    @Override
    @Transactional
    public void insertPending(NewPayment payment) {
        jdbc.update(INSERT_PENDING, keyOf(payment.orderId(), payment.provider())
            .addValue("id", payment.id())
            .addValue("orderNumber", payment.orderNumber())
            .addValue("amountCents", payment.amount().cents())
            .addValue("createdAt", payment.createdAt().atOffset(ZoneOffset.UTC)));
    }

    @Override
    @Transactional
    public boolean recordCheckout(UUID orderId, PaymentProvider provider, Checkout checkout, Instant at) {
        return jdbc.update(RECORD_CHECKOUT, keyOf(orderId, provider)
            .addValue("checkoutId", checkout.id())
            .addValue("checkoutUrl", checkout.url().toString())
            .addValue("at", at.atOffset(ZoneOffset.UTC))) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<PaymentToConfirm> lock(PaymentProvider provider, long orderNumber) {
        return jdbc.query(LOCK_BY_ORDER_NUMBER, new MapSqlParameterSource("provider", provider.name())
                .addValue("orderNumber", orderNumber),
            (row, n) -> new PaymentToConfirm(row.getObject("order_id", UUID.class), row.getLong("order_number"),
                PaymentStatus.valueOf(row.getString("status")), Money.of(row.getLong("amount_cents"))))
            .stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean record(UUID orderId, PaymentProvider provider, PaymentStatus status, String externalPaymentId,
                          Instant at) {
        if (status == PaymentStatus.PENDING) {
            throw new IllegalArgumentException("only a verified approval or rejection is recorded");
        }
        return jdbc.update(RECORD_VERIFICATION, keyOf(orderId, provider)
            .addValue("status", status.name())
            .addValue("externalPaymentId", externalPaymentId)
            .addValue("at", at.atOffset(ZoneOffset.UTC))) == 1;
    }

    private static MapSqlParameterSource keyOf(UUID orderId, PaymentProvider provider) {
        return new MapSqlParameterSource("orderId", orderId).addValue("provider", provider.name());
    }
}
