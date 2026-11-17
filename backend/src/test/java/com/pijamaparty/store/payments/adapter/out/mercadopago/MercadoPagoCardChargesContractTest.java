package com.pijamaparty.store.payments.adapter.out.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.pijamaparty.store.payments.api.CardRejection;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.CardCharges.Charge;
import com.pijamaparty.store.payments.application.CardCharges.ChargeStatus;
import com.pijamaparty.store.payments.application.CardChargesContractTest;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.support.StaticMessageSource;

/**
 * {@link MercadoPagoCardCharges} against a {@link MercadoPagoStub} of the Orders API: the contract of every card
 * provider, plus the order MercadoPago is asked to create (UC-14, T-E3-29, T-E3-34).
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
        stub.cardsAnswer("failed", "insufficient_amount");
    }

    @Override
    protected void cardsAreInReview() {
        stub.cardsAnswer("processing", "in_process");
    }

    @Override
    protected void tokensAreRefused() {
        stub.answer(400, "{\"errors\":[{\"code\":\"invalid_card_token\",\"message\":\"Card token not found\"}]}");
    }

    @Override
    protected void providerIsDown() {
        stub.answer(500, "{\"errors\":[{\"code\":\"internal_error\",\"message\":\"internal error\"}]}");
    }

    @Override
    protected void credentialsAreRefused() {
        stub.answer(401, "{\"errors\":[{\"code\":\"unauthorized\",\"message\":\"Unauthorized use of live "
            + "credentials\"}]}");
    }

    @AfterEach
    void stopStub() {
        stub.stop();
    }

    @Test
    void givenACard_whenCharged_thenAnOrderIsCreatedForTheServersAmountWithTheCardAndTheOrderNumber() {
        subject.charge(charge(1042, UUID.randomUUID()));

        var request = stub.lastRequest();
        JsonNode order = request.json();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v1/orders");
        assertThat(request.authorization()).isEqualTo("Bearer " + TOKEN);
        assertThat(order.get("type").asText()).isEqualTo("online");
        assertThat(order.get("processing_mode").asText()).isEqualTo("automatic");
        assertThat(order.get("total_amount").asText()).isEqualTo("44500.00");
        assertThat(order.get("external_reference").asText()).isEqualTo("1042");
        assertThat(order.get("description").asText()).isEqualTo("Pedido #1042 - Pijama Party");
        assertThat(order.get("payer").get("email").asText()).isEqualTo("ana@example.com");
        assertThat(order.get("payer").get("identification").get("number").asText()).isEqualTo("12345678");
        assertThat(order.has("notification_url")).isFalse();
        JsonNode payment = order.get("transactions").get("payments").get(0);
        assertThat(order.get("transactions").get("payments")).hasSize(1);
        assertThat(payment.get("amount").asText()).isEqualTo("44500.00");
        JsonNode method = payment.get("payment_method");
        assertThat(method.get("id").asText()).isEqualTo("visa");
        assertThat(method.get("type").asText()).isEqualTo("credit_card");
        assertThat(method.get("token").asText()).isEqualTo("card-token-1");
        assertThat(method.get("installments").asInt()).isEqualTo(3);
        assertThat(method.get("issuer_id").asText()).isEqualTo("310");
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
    void givenAnApprovedCard_whenCharged_thenThePaymentIdIsMercadoPagosOrderId() {
        var result = (Charge.Charged) subject.charge(charge(1047, UUID.randomUUID()));

        assertThat(result.paymentId()).startsWith("ORD");
    }

    @Test
    void givenARefusedCard_whenMercadoPagoAnswers402_thenTheRejectionComesWithItsOrder() {
        stub.cardsAnswer("failed", "rejected_by_issuer");

        var result = subject.charge(charge(1048, UUID.randomUUID()));

        assertThat(result).isInstanceOfSatisfying(Charge.Charged.class, charged -> {
            assertThat(charged.paymentId()).startsWith("ORD");
            assertThat(charged.status()).isEqualTo(ChargeStatus.REJECTED);
            assertThat(charged.rejection()).contains(CardRejection.OTHER);
        });
    }

    @ParameterizedTest
    @CsvSource({
        "insufficient_amount, INSUFFICIENT_FUNDS",
        "card_insufficient_amount, INSUFFICIENT_FUNDS",
        "bad_filled_card_data, BAD_CARD_DATA",
        "required_call_for_authorize, CALL_FOR_AUTHORIZE",
        "card_disabled, CARD_DISABLED",
        "max_attempts_exceeded, MAX_ATTEMPTS",
        "high_risk, HIGH_RISK",
        "amount_limit_exceeded, OTHER",
        "something_new, OTHER"
    })
    void givenARejectionReason_whenCharged_thenItIsTheStoresWord(String detail, CardRejection expected) {
        stub.cardsAnswer("failed", detail);

        var result = subject.charge(charge(1044, UUID.randomUUID()));

        assertThat(((Charge.Charged) result).rejection()).contains(expected);
    }

    @Test
    void givenA402WithoutAnOrder_whenCharged_thenTheChargeIsRefused() {
        stub.answer(402, "{\"errors\":[{\"code\":\"failed\",\"message\":\"The following transactions failed\"}]}");

        assertThat(subject.charge(charge(1049, UUID.randomUUID()))).isInstanceOf(Charge.Refused.class);
    }

    @Test
    void givenTheStoresCredentialsForbidden_whenCharged_thenItIsTheCredentials() {
        stub.answer(403, "{\"errors\":[{\"code\":\"forbidden\",\"message\":\"forbidden\"}]}");

        assertThat(subject.charge(charge(1050, UUID.randomUUID()))).isInstanceOf(Charge.CredentialsRefused.class);
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
        return new MercadoPagoCardCharges(properties, token, publicKey, messages);
    }
}
