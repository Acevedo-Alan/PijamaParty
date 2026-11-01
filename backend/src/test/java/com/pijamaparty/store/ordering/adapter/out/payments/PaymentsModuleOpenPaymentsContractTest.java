package com.pijamaparty.store.ordering.adapter.out.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.application.OpenPayments;
import com.pijamaparty.store.ordering.application.OpenPaymentsContractTest;
import com.pijamaparty.store.ordering.application.OrdersToPay.OrderToPay;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.payments.adapter.out.mercadopago.MercadoPagoStub;
import com.pijamaparty.store.payments.adapter.out.modo.ModoStub;
import com.pijamaparty.store.payments.adapter.out.modo.ModoStubConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Through the public API of the payments module to the {@code payment} table: MercadoPago paid on the store's page
 * (T-E3-29) and MODO against its local stub. Each call outside any transaction, as the checkout opens it; the adapter
 * runs as SYSTEM.
 */
@ApiTest
@Import(ModoStubConfiguration.class)
class PaymentsModuleOpenPaymentsContractTest extends OpenPaymentsContractTest<OpenPayments> {

    private final PaymentsModuleOpenPayments adapter;
    private final MercadoPagoStub mercadoPago;
    private final ModoStub modo;
    private final JdbcTemplate owner;

    PaymentsModuleOpenPaymentsContractTest(PaymentsModuleOpenPayments adapter, MercadoPagoStub mercadoPago,
                                           ModoStub modo, PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.mercadoPago = mercadoPago;
        this.modo = modo;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected OpenPayments createSubject() {
        return adapter;
    }

    @Override
    protected void providerIsDown() {
        modo.answerPaymentRequests(503, "{\"message\":\"unavailable\"}");
    }

    @Override
    protected void providerIsBack() {
        modo.reset();
    }

    @AfterEach
    void cleanUp() {
        mercadoPago.reset();
        modo.reset();
        owner.execute("truncate payment_checkout, payment");
    }

    @Test
    void givenAMercadoPagoOrder_whenItsPaymentIsOpened_thenThePendingPaymentIsStoredForTheOrderTotalWithoutCalling() {
        OrderToPay order = orderToPay(PaymentProvider.MERCADOPAGO);

        subject.open(order);
        subject.open(order);

        Map<String, Object> payment = owner.queryForMap("select * from payment where order_id = ?",
            order.id().value());
        assertThat(payment).containsEntry("provider", "MERCADOPAGO").containsEntry("status", "PENDING")
            .containsEntry("amount_cents", 4_450_000L).containsEntry("checkout_url", null);
        assertThat(owner.queryForObject("select count(*) from payment", Long.class)).isOne();
        assertThat(mercadoPago.requests()).isEmpty();
    }
}
