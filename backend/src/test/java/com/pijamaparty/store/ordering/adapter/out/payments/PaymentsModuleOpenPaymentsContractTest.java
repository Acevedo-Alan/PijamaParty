package com.pijamaparty.store.ordering.adapter.out.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.ordering.application.OpenPayments;
import com.pijamaparty.store.ordering.application.OpenPaymentsContractTest;
import com.pijamaparty.store.ordering.application.OrdersToPay.OrderToPay;
import com.pijamaparty.store.payments.adapter.out.mercadopago.MercadoPagoStub;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Through the public API of the payments module to the {@code payment} table and the MercadoPago gateway, which
 * talks to a local stub. Each call outside any transaction, as the checkout opens it; the adapter runs as SYSTEM.
 */
@ApiTest
class PaymentsModuleOpenPaymentsContractTest extends OpenPaymentsContractTest<OpenPayments> {

    private final PaymentsModuleOpenPayments adapter;
    private final MercadoPagoStub mercadoPago;
    private final JdbcTemplate owner;

    PaymentsModuleOpenPaymentsContractTest(PaymentsModuleOpenPayments adapter, MercadoPagoStub mercadoPago,
                                           PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.mercadoPago = mercadoPago;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected OpenPayments createSubject() {
        return adapter;
    }

    @Override
    protected void providerIsDown() {
        mercadoPago.answer(503, "{\"message\":\"unavailable\"}");
    }

    @Override
    protected void providerIsBack() {
        mercadoPago.reset();
    }

    @AfterEach
    void cleanUp() {
        mercadoPago.reset();
        owner.execute("truncate payment");
    }

    @Test
    void givenAnOrderAwaitingPayment_whenItsPaymentIsOpened_thenThePendingPaymentHoldsTheCheckoutForTheOrderTotal() {
        OrderToPay order = orderToPay();

        var url = subject.open(order).orElseThrow();

        Map<String, Object> payment = owner.queryForMap("select * from payment where order_id = ?",
            order.id().value());
        assertThat(payment).containsEntry("provider", "MERCADOPAGO").containsEntry("status", "PENDING")
            .containsEntry("amount_cents", 4_450_000L).containsEntry("checkout_url", url.toString());
        JsonNode preference = mercadoPago.lastRequest().json();
        assertThat(preference.get("external_reference").asText()).isEqualTo(Long.toString(order.number()));
        assertThat(preference.get("items").get(0).get("unit_price").decimalValue()).isEqualByComparingTo("44500.00");
    }

    @Test
    void givenAnOrderWhosePaymentWasOpened_whenItIsOpenedAgain_thenTheProviderIsCalledOnce() {
        OrderToPay order = orderToPay();
        subject.open(order);

        subject.open(order);

        assertThat(mercadoPago.requests()).hasSize(1);
        assertThat(owner.queryForObject("select count(*) from payment", Long.class)).isOne();
    }
}
