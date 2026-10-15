package com.pijamaparty.store.payments.adapter.out.persistence;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.CheckoutsToReconcile;
import com.pijamaparty.store.payments.application.LoadPayments;
import com.pijamaparty.store.payments.application.PaymentConfirmations;
import com.pijamaparty.store.payments.application.PaymentsByCheckout;
import com.pijamaparty.store.payments.application.PaymentsToConfirm;
import com.pijamaparty.store.payments.application.SavePayments;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.payments.domain.CheckoutAttempt;
import com.pijamaparty.store.payments.domain.PaymentStatus;
import com.pijamaparty.store.platform.domain.Money;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payments in table {@code payment} (V15, V18) and their checkout attempts in {@code payment_checkout} (V27). Opening
 * a payment runs each call in its own transaction (or joins the caller's), so the payment use case commits before
 * calling the provider (RNF-21); confirming one locks and records it in the caller's transaction, and finds MODO's
 * payments by their checkout (T-E3b-03). Row Level Security lets only the system write (RNF-27).
 */
@Repository
class JdbcPaymentStore implements LoadPayments, SavePayments, PaymentsToConfirm, PaymentConfirmations,
    PaymentsByCheckout, CheckoutsToReconcile {

    // The payment's current checkout, with the expiry its attempt recorded (T-E3b-02).
    private static final String SELECT_CHECKOUT = """
        SELECT p.checkout_id, p.checkout_url, c.expires_at FROM payment p
        LEFT JOIN payment_checkout c
            ON c.payment_id = p.id AND c.provider = p.provider AND c.checkout_id = p.checkout_id
        WHERE p.order_id = :orderId AND p.provider = :provider AND p.checkout_id IS NOT NULL
        """;

    // RNF-20.8: one payment per order and provider (payment_order_provider_uk); a concurrent insert that loses does
    // nothing, and the winner's row is the payment.
    private static final String INSERT_PENDING = """
        INSERT INTO payment (id, order_id, order_number, provider, amount_cents, status, created_at, updated_at)
        VALUES (:id, :orderId, :orderNumber, :provider, :amountCents, 'PENDING', :createdAt, :createdAt)
        ON CONFLICT (order_id, provider) DO NOTHING
        """;

    // T-E3b-02: starting an attempt and recording a checkout lock the payment row, so they run one at a time.
    private static final String LOCK_PAYMENT = """
        SELECT id, order_number FROM payment WHERE order_id = :orderId AND provider = :provider
        FOR UPDATE
        """;

    private static final String NEXT_ATTEMPT = """
        SELECT COALESCE(MAX(attempt), 0) + 1 FROM payment_checkout WHERE payment_id = :paymentId
        """;

    private static final String INSERT_ATTEMPT = """
        INSERT INTO payment_checkout (payment_id, provider, attempt, external_reference, created_at)
        VALUES (:paymentId, :provider, :attempt, :externalReference, :at)
        """;

    // MercadoPago answers the same preference to two attempts of an order (T-E3-17): it is recorded once.
    private static final String CHECKOUT_RECORDED = """
        SELECT EXISTS (SELECT 1 FROM payment_checkout WHERE provider = :provider AND checkout_id = :checkoutId)
        """;

    // Conditional, so the first checkout recorded in an attempt stays the attempt's.
    private static final String RECORD_ATTEMPT_CHECKOUT = """
        UPDATE payment_checkout SET checkout_id = :checkoutId, checkout_url = :checkoutUrl, expires_at = :expiresAt
        WHERE payment_id = :paymentId AND attempt = :attempt AND checkout_id IS NULL
        """;

    // The latest attempt with a checkout is the payment's current one.
    private static final String RECORD_CURRENT_CHECKOUT = """
        UPDATE payment SET checkout_id = :checkoutId, checkout_url = :checkoutUrl, updated_at = :at
        WHERE id = :paymentId
          AND NOT EXISTS (SELECT 1 FROM payment_checkout
                          WHERE payment_id = :paymentId AND attempt > :attempt AND checkout_id IS NOT NULL)
        """;

    // UC-15: over payment_provider_order_number_uk. The lock makes two notifications of the payment wait for each
    // other (RNF-20.3).
    private static final String LOCK_BY_ORDER_NUMBER = """
        SELECT order_id, order_number, status, amount_cents FROM payment
        WHERE provider = :provider AND order_number = :orderNumber
        FOR UPDATE
        """;

    // T-E3b-03: over payment_checkout_provider_checkout_uk.
    private static final String ORDER_NUMBER_BY_CHECKOUT = """
        SELECT p.order_number FROM payment_checkout c
        JOIN payment p ON p.id = c.payment_id AND p.provider = c.provider
        WHERE c.provider = :provider AND c.checkout_id = :checkoutId
        """;

    // T-E3b-03: over payment_checkout_expires_idx (V28). A checkout without expiry (MercadoPago) is never due.
    private static final String DUE_CHECKOUTS = """
        SELECT c.provider, c.checkout_id FROM payment_checkout c
        JOIN payment p ON p.id = c.payment_id AND p.provider = c.provider
        WHERE c.provider IN (:providers) AND c.checkout_id IS NOT NULL
          AND c.created_at < :createdBefore AND c.expires_at > :expiringAfter
          AND p.status IN (:statuses)
        ORDER BY c.created_at, c.payment_id, c.attempt
        LIMIT :limit
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
                (row, n) -> new Checkout(row.getString("checkout_id"), URI.create(row.getString("checkout_url")),
                    Optional.ofNullable(row.getObject("expires_at", OffsetDateTime.class))
                        .map(OffsetDateTime::toInstant)))
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
    public Optional<CheckoutAttempt> startAttempt(UUID orderId, PaymentProvider provider, Instant at) {
        Optional<LockedPayment> payment = lockPayment(orderId, provider);
        if (payment.isEmpty()) {
            return Optional.empty();
        }
        var values = new MapSqlParameterSource("paymentId", payment.get().id());
        Integer next = jdbc.queryForObject(NEXT_ATTEMPT, values, Integer.class);
        CheckoutAttempt attempt = CheckoutAttempt.of(payment.get().orderNumber(), Objects.requireNonNull(next));
        jdbc.update(INSERT_ATTEMPT, values
            .addValue("provider", provider.name())
            .addValue("attempt", attempt.number())
            .addValue("externalReference", attempt.externalReference())
            .addValue("at", at.atOffset(ZoneOffset.UTC)));
        return Optional.of(attempt);
    }

    @Override
    @Transactional
    public boolean recordCheckout(UUID orderId, PaymentProvider provider, int attempt, Checkout checkout,
                                  Instant at) {
        Optional<LockedPayment> payment = lockPayment(orderId, provider);
        if (payment.isEmpty()) {
            return false;
        }
        var values = new MapSqlParameterSource("paymentId", payment.get().id())
            .addValue("provider", provider.name())
            .addValue("attempt", attempt)
            .addValue("checkoutId", checkout.id())
            .addValue("checkoutUrl", checkout.url().toString())
            .addValue("expiresAt", checkout.expiresAt().map(expiry -> expiry.atOffset(ZoneOffset.UTC)).orElse(null))
            .addValue("at", at.atOffset(ZoneOffset.UTC));
        if (Boolean.TRUE.equals(jdbc.queryForObject(CHECKOUT_RECORDED, values, Boolean.class))
            || jdbc.update(RECORD_ATTEMPT_CHECKOUT, values) == 0) {
            return false;
        }
        return jdbc.update(RECORD_CURRENT_CHECKOUT, values) == 1;
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

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public OptionalLong orderNumberOf(PaymentProvider provider, String checkoutId) {
        return jdbc.query(ORDER_NUMBER_BY_CHECKOUT, new MapSqlParameterSource("provider", provider.name())
                .addValue("checkoutId", checkoutId), (row, n) -> row.getLong("order_number"))
            .stream().mapToLong(Long::longValue).findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckoutToReconcile> due(Set<PaymentProvider> providers, Set<PaymentStatus> statuses,
                                        Instant createdBefore, Instant expiringAfter, int limit) {
        if (providers.isEmpty() || statuses.isEmpty()) {
            return List.of();
        }
        return jdbc.query(DUE_CHECKOUTS, new MapSqlParameterSource("providers",
                    providers.stream().map(PaymentProvider::name).toList())
                .addValue("statuses", statuses.stream().map(PaymentStatus::name).toList())
                .addValue("createdBefore", createdBefore.atOffset(ZoneOffset.UTC))
                .addValue("expiringAfter", expiringAfter.atOffset(ZoneOffset.UTC))
                .addValue("limit", limit),
            (row, n) -> new CheckoutToReconcile(PaymentProvider.valueOf(row.getString("provider")),
                row.getString("checkout_id")));
    }

    private Optional<LockedPayment> lockPayment(UUID orderId, PaymentProvider provider) {
        return jdbc.query(LOCK_PAYMENT, keyOf(orderId, provider),
                (row, n) -> new LockedPayment(row.getObject("id", UUID.class), row.getLong("order_number")))
            .stream().findFirst();
    }

    private record LockedPayment(UUID id, long orderNumber) {}

    private static MapSqlParameterSource keyOf(UUID orderId, PaymentProvider provider) {
        return new MapSqlParameterSource("orderId", orderId).addValue("provider", provider.name());
    }
}
