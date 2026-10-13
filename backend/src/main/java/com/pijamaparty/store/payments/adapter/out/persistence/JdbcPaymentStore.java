package com.pijamaparty.store.payments.adapter.out.persistence;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.LoadPayments;
import com.pijamaparty.store.payments.application.SavePayments;
import com.pijamaparty.store.payments.domain.Checkout;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payments in table {@code payment} (V15). Each call joins the caller's transaction or runs in its own, so the payment
 * use case commits before calling the provider (RNF-21). Row Level Security lets only the system write (RNF-27).
 */
@Repository
class JdbcPaymentStore implements LoadPayments, SavePayments {

    private static final String SELECT_CHECKOUT = """
        SELECT checkout_id, checkout_url FROM payment
        WHERE order_id = :orderId AND provider = :provider AND checkout_id IS NOT NULL
        """;

    // RNF-20.8: one payment per order and provider (payment_order_provider_uk); a concurrent insert that loses does
    // nothing, and the winner's row is the payment.
    private static final String INSERT_PENDING = """
        INSERT INTO payment (id, order_id, provider, amount_cents, status, created_at, updated_at)
        VALUES (:id, :orderId, :provider, :amountCents, 'PENDING', :createdAt, :createdAt)
        ON CONFLICT (order_id, provider) DO NOTHING
        """;

    // Conditional, so the first checkout recorded stays the payment's.
    private static final String RECORD_CHECKOUT = """
        UPDATE payment SET checkout_id = :checkoutId, checkout_url = :checkoutUrl, updated_at = :at
        WHERE order_id = :orderId AND provider = :provider AND checkout_id IS NULL
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

    private static MapSqlParameterSource keyOf(UUID orderId, PaymentProvider provider) {
        return new MapSqlParameterSource("orderId", orderId).addValue("provider", provider.name());
    }
}
