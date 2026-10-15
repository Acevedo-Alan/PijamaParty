package com.pijamaparty.store.payments.adapter.out.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.PaymentGateway;
import com.pijamaparty.store.payments.application.PaymentGatewayContractTest;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.payments.domain.CheckoutAttempt;
import com.pijamaparty.store.platform.domain.Money;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * {@link MercadoPagoGateway} against a {@link MercadoPagoStub}: the contract of every gateway, plus the preference it
 * sends (UC-14, T-E3-06) and how it survives failures (RNF-21).
 */
class MercadoPagoGatewayContractTest extends PaymentGatewayContractTest<MercadoPagoGateway> {

    private static final String TOKEN = "TEST-1234567890-access-token";
    private static final URI PUBLIC_URL = URI.create("https://pijamaparty.example/");

    private final MercadoPagoStub stub = new MercadoPagoStub();

    @Override
    protected MercadoPagoGateway createSubject() {
        return gateway(TOKEN, Duration.ofSeconds(2));
    }

    @Override
    protected PaymentProvider expectedProvider() {
        return PaymentProvider.MERCADOPAGO;
    }

    @Override
    protected void providerIsDown() {
        stub.answer(500, "{\"message\":\"internal_error\"}");
    }

    /** MercadoPago charges the items of the preference: unit price times quantity, in pesos. */
    @Override
    protected Money chargedAmount(Checkout checkout) {
        long cents = 0;
        for (JsonNode item : stub.lastRequest().json().get("items")) {
            cents += item.get("unit_price").decimalValue().movePointRight(2).longValueExact()
                * item.get("quantity").asLong();
        }
        return Money.of(cents);
    }

    @AfterEach
    void stopStub() {
        stub.stop();
    }

    @Test
    void givenAConfirmedOrder_whenTheCheckoutIsCreated_thenTheAnswersIdAndInitPointAreTheCheckout() {
        PaymentGateway.Outcome outcome = subject.createCheckout(REQUEST, ATTEMPT);

        assertThat(outcome).isEqualTo(new PaymentGateway.Outcome.Created(
            new Checkout("pref-1", URI.create(MercadoPagoStub.INIT_POINT + "pref-1"))));
    }

    @Test
    void givenAConfirmedOrder_whenTheCheckoutIsCreated_thenThePreferenceIsOneLineForTheOrderTotal() {
        subject.createCheckout(REQUEST, ATTEMPT);

        assertThat(stub.lastRequest().path()).isEqualTo("/checkout/preferences");
        assertThat(stub.lastRequest().authorization()).isEqualTo("Bearer " + TOKEN);
        JsonNode body = stub.lastRequest().json();
        assertThat(body.get("items")).hasSize(1);
        JsonNode item = body.get("items").get(0);
        assertThat(item.get("id").asText()).isEqualTo("1001");
        assertThat(item.get("title").asText()).isEqualTo("Pedido #1001 - Pijama Party");
        assertThat(item.get("quantity").asInt()).isOne();
        assertThat(item.get("unit_price").decimalValue()).isEqualByComparingTo("21000.50");
        assertThat(item.get("currency_id").asText()).isEqualTo("ARS");
        assertThat(body.get("external_reference").asText()).isEqualTo("1001");
    }

    @Test
    void givenAnOrder_whenItsCheckoutIsCreatedTwice_thenBothCarryTheSameIdempotencyKeyAndGetTheSamePreference() {
        PaymentGateway.Outcome first = subject.createCheckout(REQUEST, ATTEMPT);
        PaymentGateway.Outcome again = subject.createCheckout(REQUEST, ATTEMPT);

        assertThat(again).isEqualTo(first);
        assertThat(stub.requests()).extracting(MercadoPagoStub.Request::idempotencyKey)
            .containsExactly(MercadoPagoGateway.idempotencyKeyOf(1001), MercadoPagoGateway.idempotencyKeyOf(1001));
        assertThat(UUID.fromString(stub.lastRequest().idempotencyKey()).toString())
            .isEqualTo(stub.lastRequest().idempotencyKey());
    }

    @Test
    void givenAnOrder_whenItsCheckoutIsCreatedTwiceAtOnce_thenBothGetTheSamePreference() throws Exception {
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<PaymentGateway.Outcome> create = () -> {
                start.await(10, TimeUnit.SECONDS);
                return subject.createCheckout(REQUEST, ATTEMPT);
            };
            Future<PaymentGateway.Outcome> first = executor.submit(create);
            Future<PaymentGateway.Outcome> second = executor.submit(create);
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)).isInstanceOf(PaymentGateway.Outcome.Created.class)
                .isEqualTo(second.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        assertThat(stub.requests()).extracting(MercadoPagoStub.Request::idempotencyKey)
            .containsOnly(MercadoPagoGateway.idempotencyKeyOf(1001)).hasSize(2);
    }

    @Test
    void givenASecondAttempt_whenTheCheckoutIsCreated_thenTheOrderNumberStaysTheReferenceAndTheKey() {
        PaymentGateway.Outcome first = subject.createCheckout(REQUEST, ATTEMPT);

        PaymentGateway.Outcome second = subject.createCheckout(REQUEST, CheckoutAttempt.of(1001, 2));

        assertThat(second).isEqualTo(first);
        assertThat(stub.lastRequest().json().get("external_reference").asText()).isEqualTo("1001");
        assertThat(stub.lastRequest().idempotencyKey()).isEqualTo(MercadoPagoGateway.idempotencyKeyOf(1001));
        assertThat(((PaymentGateway.Outcome.Created) second).checkout().expiresAt()).isEmpty();
    }

    @Test
    void givenTwoOrders_whenTheirCheckoutsAreCreated_thenEachHasItsOwnIdempotencyKey() {
        assertThat(MercadoPagoGateway.idempotencyKeyOf(1001)).isNotEqualTo(MercadoPagoGateway.idempotencyKeyOf(1002));
    }

    @Test
    void givenAConfirmedOrder_whenTheCheckoutIsCreated_thenOnlyTheBuyersNameAndEmailGoToTheProvider() {
        subject.createCheckout(REQUEST, ATTEMPT);

        JsonNode payer = stub.lastRequest().json().get("payer");
        List<String> fields = new ArrayList<>();
        payer.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("name", "email");
        assertThat(payer.get("name").asText()).isEqualTo("Ana Pérez");
        assertThat(payer.get("email").asText()).isEqualTo("ana@example.com");
    }

    @Test
    void givenAConfirmedOrder_whenTheCheckoutIsCreated_thenTheBuyerComesBackToTheStoreWithoutTheTrackingToken() {
        subject.createCheckout(REQUEST, ATTEMPT);

        JsonNode body = stub.lastRequest().json();
        for (String result : List.of("success", "failure", "pending")) {
            assertThat(body.get("back_urls").get(result).asText())
                .isEqualTo("https://pijamaparty.example/checkout/result");
        }
        assertThat(body.get("auto_return").asText()).isEqualTo("approved");
        assertThat(body.get("notification_url").asText())
            .isEqualTo("https://pijamaparty.example/api/payments/mercadopago/webhook?source_news=webhooks");
    }

    @Test
    void givenAConfirmedOrder_whenTheCheckoutIsCreated_thenOnlyImmediateMethodsAndItExpiresWithTheReservation() {
        subject.createCheckout(REQUEST, ATTEMPT);

        JsonNode body = stub.lastRequest().json();
        List<String> excluded = new ArrayList<>();
        body.get("payment_methods").get("excluded_payment_types")
            .forEach(type -> excluded.add(type.get("id").asText()));
        assertThat(excluded).containsExactlyInAnyOrder("ticket", "atm");
        assertThat(body.get("expires").asBoolean()).isTrue();
        // 15:30 UTC is 12:30 in Argentina
        assertThat(body.get("expiration_date_to").asText()).isEqualTo("2026-10-12T12:30:00.000-03:00");
    }

    @Test
    void givenNoAccessToken_whenTheCheckoutIsCreated_thenItIsUnavailableWithoutCallingTheProvider() {
        MercadoPagoGateway unconfigured = gateway("", Duration.ofSeconds(2));

        assertThat(unconfigured.createCheckout(REQUEST, ATTEMPT)).isInstanceOf(PaymentGateway.Outcome.Unavailable.class);
        assertThat(stub.requests()).isEmpty();
    }

    @Test
    void givenTheProviderRefusesTheRequest_whenTheCheckoutIsCreated_thenItIsUnavailable() {
        stub.answer(400, "{\"message\":\"invalid items\",\"status\":400}");

        assertThat(subject.createCheckout(REQUEST, ATTEMPT)).isInstanceOf(PaymentGateway.Outcome.Unavailable.class);
    }

    @Test
    void givenTheProviderTakesLongerThanTheReadTimeout_whenTheCheckoutIsCreated_thenItIsUnavailable() {
        MercadoPagoGateway impatient = gateway(TOKEN, Duration.ofMillis(200));
        stub.delay(Duration.ofSeconds(2));

        assertThat(impatient.createCheckout(REQUEST, ATTEMPT)).isInstanceOf(PaymentGateway.Outcome.Unavailable.class);
    }

    @Test
    void givenTheProviderIsUnreachable_whenTheCheckoutIsCreated_thenItIsUnavailable() {
        stub.stop();

        assertThat(subject.createCheckout(REQUEST, ATTEMPT)).isInstanceOf(PaymentGateway.Outcome.Unavailable.class);
    }

    @Test
    void givenAnAnswerWithoutAnHttpsInitPoint_whenTheCheckoutIsCreated_thenItIsUnavailable() {
        stub.answer(201, "{\"id\":\"123-pref\",\"init_point\":\"http://www.mercadopago.com.ar/checkout\"}");

        assertThat(subject.createCheckout(REQUEST, ATTEMPT)).isInstanceOf(PaymentGateway.Outcome.Unavailable.class);
    }

    @Test
    void givenAnAnswerWithoutAnId_whenTheCheckoutIsCreated_thenItIsUnavailable() {
        stub.answer(201, "{\"init_point\":\"" + MercadoPagoStub.INIT_POINT + "x\"}");

        assertThat(subject.createCheckout(REQUEST, ATTEMPT)).isInstanceOf(PaymentGateway.Outcome.Unavailable.class);
    }

    @Test
    void givenAnAnswerThatIsNotJson_whenTheCheckoutIsCreated_thenItIsUnavailable() {
        stub.answer(201, "<html>maintenance</html>");

        assertThat(subject.createCheckout(REQUEST, ATTEMPT)).isInstanceOf(PaymentGateway.Outcome.Unavailable.class);
    }

    private MercadoPagoGateway gateway(String token, Duration readTimeout) {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        var properties = new MercadoPagoProperties(stub.baseUrl(), Duration.ofSeconds(1), readTimeout);
        return new MercadoPagoGateway(properties, token, PUBLIC_URL, messages);
    }
}
