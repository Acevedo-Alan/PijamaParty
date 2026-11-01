package com.pijamaparty.store.payments.adapter.out.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.pijamaparty.store.payments.api.CardRejection;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.CardCharges.Charge;
import com.pijamaparty.store.payments.application.CardChargesContractTest;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

/**
 * {@link MercadoPagoCardCharges} against a {@link MercadoPagoStub}: the contract of every card provider, plus the
 * payment MercadoPago is asked to create (UC-14, T-E3-29).
 */
class MercadoPagoCardChargesContractTest extends CardChargesContractTest<MercadoPagoCardCharges> {

    private static final String TOKEN = "TEST-1234567890-access-token";

    private final MercadoPagoStub stub = new MercadoPagoStub();

    @Override
    protected MercadoPagoCardCharges createSubject() {
        return charges(TOKEN, "TEST-public-key");
    }

    @Override
    protected PaymentProvider expectedProvider() {
        return PaymentProvider.MERCADOPAGO;
    }

    @Override
    protected void cardsAreRejectedForInsufficientFunds() {
        stub.cardsAnswer("rejected", "cc_rejected_insufficient_amount");
    }

    @Override
    protected void cardsAreInReview() {
        stub.cardsAnswer("in_process", "pending_contingency");
    }

    @Override
    protected void tokensAreRefused() {
        stub.answer(400, "{\"message\":\"Card token not found\",\"status\":400}");
    }

    @Override
    protected void providerIsDown() {
        stub.answer(500, "{\"message\":\"internal_error\"}");
    }

    @AfterEach
    void stopStub() {
        stub.stop();
    }

    @Test
    void givenACard_whenCharged_thenMercadoPagoIsAskedForTheServersAmountWithTheCardAndTheOrder() {
        subject.charge(charge(1042, UUID.randomUUID()));

        var request = stub.lastRequest();
        JsonNode payment = request.json();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v1/payments");
        assertThat(request.authorization()).isEqualTo("Bearer " + TOKEN);
        assertThat(payment.get("transaction_amount").decimalValue()).isEqualByComparingTo("44500.00");
        assertThat(payment.get("token").asText()).isEqualTo("card-token-1");
        assertThat(payment.get("installments").asInt()).isEqualTo(3);
        assertThat(payment.get("payment_method_id").asText()).isEqualTo("visa");
        assertThat(payment.get("issuer_id").asText()).isEqualTo("310");
        assertThat(payment.get("payer").get("email").asText()).isEqualTo("ana@example.com");
        assertThat(payment.get("payer").get("identification").get("number").asText()).isEqualTo("12345678");
        assertThat(payment.get("external_reference").asText()).isEqualTo("1042");
        assertThat(payment.get("description").asText()).isEqualTo("Pedido #1042 - Pijama Party");
        assertThat(payment.get("notification_url").asText())
            .isEqualTo("https://tienda.example/api/payments/mercadopago/webhook?source_news=webhooks");
    }

    @Test
    void givenASubmit_whenCharged_thenItsIdempotencyKeyIsDerivedFromTheOrderAndTheSubmit() {
        UUID submit = UUID.randomUUID();

        subject.charge(charge(1042, submit));

        assertThat(stub.lastRequest().idempotencyKey())
            .isEqualTo(MercadoPagoCardCharges.idempotencyKeyOf(1042, submit))
            .isNotEqualTo(MercadoPagoCardCharges.idempotencyKeyOf(1043, submit))
            .isNotEqualTo(MercadoPagoCardCharges.idempotencyKeyOf(1042, UUID.randomUUID()));
    }

    @Test
    void givenAnUnknownRejection_whenCharged_thenItIsOther() {
        stub.cardsAnswer("rejected", "cc_rejected_something_new");

        var result = subject.charge(charge(1044, UUID.randomUUID()));

        assertThat(((Charge.Charged) result).rejection()).contains(CardRejection.OTHER);
    }

    @Test
    void givenNoAccessToken_whenCharged_thenItIsUnavailableWithoutCallingMercadoPago() {
        var unconfigured = charges("", "");

        assertThat(unconfigured.charge(charge(1045, UUID.randomUUID()))).isInstanceOf(Charge.Unavailable.class);
        assertThat(stub.requests()).isEmpty();
        assertThat(unconfigured.publicKey()).isEmpty();
    }

    @Test
    void givenThePublicKey_whenAsked_thenItIsTheConfiguredOne() {
        assertThat(subject.publicKey()).contains("TEST-public-key");
    }

    @Test
    void givenMercadoPagoTooSlow_whenCharged_thenItIsUnavailable() {
        stub.delay(Duration.ofMillis(1500));

        assertThat(subject.charge(charge(1046, UUID.randomUUID()))).isInstanceOf(Charge.Unavailable.class);
    }

    private MercadoPagoCardCharges charges(String token, String publicKey) {
        var properties = new MercadoPagoProperties(stub.baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(1));
        var messages = new StaticMessageSource();
        messages.addMessage("payments.checkout.title", Locale.forLanguageTag("es-AR"), "Pedido #{0} - Pijama Party");
        return new MercadoPagoCardCharges(properties, token, publicKey, URI.create("https://tienda.example/"),
            messages);
    }
}
