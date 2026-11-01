package com.pijamaparty.store.ordering.adapter.out.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.adapter.out.persistence.OrderRows;
import com.pijamaparty.store.ordering.application.ChargeCards;
import com.pijamaparty.store.ordering.application.ChargeCardsContractTest;
import com.pijamaparty.store.ordering.application.OrdersToPay.OrderToPay;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.payments.adapter.out.mercadopago.MercadoPagoStub;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Through the public API of the payments module to the {@code payment} and {@code payment_event} tables and the
 * MercadoPago adapter, against its local stub (T-E3-29). Outside any transaction; the adapter runs as SYSTEM.
 */
@ApiTest
class PaymentsModuleChargeCardsContractTest extends ChargeCardsContractTest<ChargeCards> {

    private final PaymentsModuleChargeCards adapter;
    private final MercadoPagoStub mercadoPago;
    private final JdbcTemplate owner;

    PaymentsModuleChargeCardsContractTest(PaymentsModuleChargeCards adapter, MercadoPagoStub mercadoPago,
                                          PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.mercadoPago = mercadoPago;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected ChargeCards createSubject() {
        return adapter;
    }

    /** A real order awaiting payment: an approval is applied to it at once (UC-15). */
    @Override
    protected OrderToPay orderToPay() {
        OrderId id = OrderId.of(UUID.randomUUID());
        Instant expiry = Instant.now().plusSeconds(1800);
        OrderRows.insert(owner, id, OrderStatus.AWAITING_PAYMENT, expiry);
        Long total = owner.queryForObject("select total_cents from orders where id = ?", Long.class, id.value());
        return new OrderToPay(id, OrderRows.numberOf(owner, id), OrderStatus.AWAITING_PAYMENT, Money.of(total),
            PaymentProvider.MERCADOPAGO, "Ana Pérez", "ana@example.com", expiry);
    }

    @Override
    protected void cardsAreRejectedForInsufficientFunds() {
        mercadoPago.cardsAnswer("rejected", "cc_rejected_insufficient_amount");
    }

    @Override
    protected void cardsAreInReview() {
        mercadoPago.cardsAnswer("in_process", "pending_contingency");
    }

    @Override
    protected void providerIsDown() {
        mercadoPago.answer(503, "{\"message\":\"unavailable\"}");
    }

    @AfterEach
    void cleanUp() {
        mercadoPago.reset();
        owner.execute("truncate payment_event, payment_checkout, payment, email_outbox, withdrawal_request, "
            + "order_tracking_token, order_item, orders");
    }

    @Test
    void givenAnApprovedCard_whenCharged_thenTheOrdersPaymentIsApprovedAtOnceForItsTotal() {
        OrderToPay order = orderToPay();

        subject.charge(order, CARD, UUID.randomUUID());

        Map<String, Object> payment = owner.queryForMap("select * from payment where order_id = ?",
            order.id().value());
        assertThat(payment).containsEntry("provider", "MERCADOPAGO").containsEntry("status", "APPROVED")
            .containsEntry("amount_cents", order.total().cents());
        assertThat(OrderRows.statusOf(owner, order.id())).contains(OrderStatus.PAID);
        assertThat(owner.queryForObject("select count(*) from payment_event", Long.class)).isOne();
        assertThat(mercadoPago.requests()).extracting(MercadoPagoStub.Request::path)
            .containsExactly("/v1/payments", "/v1/payments/" + payment.get("external_payment_id"));
    }
}
