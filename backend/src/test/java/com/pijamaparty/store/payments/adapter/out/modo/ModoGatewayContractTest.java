package com.pijamaparty.store.payments.adapter.out.modo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.pijamaparty.store.payments.api.CheckoutRequest;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.PaymentGateway;
import com.pijamaparty.store.payments.application.PaymentGatewayContractTest;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.payments.domain.CheckoutAttempt;
import com.pijamaparty.store.platform.domain.Money;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * {@link ModoGateway} against a {@link ModoStub}: the contract of every gateway, plus the payment request it sends,
 * its expiry and the redirect to MODO's site (UC-14, T-E3b-02), the token it keeps, and how it survives failures
 * (RNF-21).
 */
class ModoGatewayContractTest extends PaymentGatewayContractTest<ModoGateway> {

    /** The reservation of {@link #REQUEST} ends at 15:30 UTC: 30 minutes after this. */
    private static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");
    private static final URI PUBLIC_URL = URI.create("https://pijamaparty.example/");
    private static final URI CHECKOUT_URL = URI.create("https://site.preprod.modo.com.ar");
    private static final String PAYMENT_REQUESTS = ModoGateway.PAYMENT_REQUESTS_PATH;
    private static final String TOKENS = ModoAccessTokens.TOKEN_PATH;

    private final ModoStub stub = new ModoStub();

    @Override
    protected ModoGateway createSubject() {
        return gateway(properties(true, "P-123", Duration.ofSeconds(2)), ModoStub.USERNAME, ModoStub.PASSWORD);
    }

    @Override
    protected PaymentProvider expectedProvider() {
        return PaymentProvider.MODO;
    }

    @Override
    protected void providerIsDown() {
        stub.answerPaymentRequests(500, "{\"message\":\"internal_error\"}");
    }

    /** MODO charges the payment request's amount, in pesos. */
    @Override
    protected Money chargedAmount(Checkout checkout) {
        JsonNode body = stub.requestsTo(PAYMENT_REQUESTS).getLast().json();
        return Money.of(body.get("amount").decimalValue().movePointRight(2).longValueExact());
    }

    @AfterEach
    void stopStub() {
        stub.stop();
    }

    @Test
    void givenAConfirmedOrder_whenTheCheckoutIsCreated_thenThePaymentRequestCarriesTheOrderAndTheAttempt() {
        subject.createCheckout(REQUEST, CheckoutAttempt.of(1001, 2));

        ModoStub.Request sent = stub.requestsTo(PAYMENT_REQUESTS).getLast();
        assertThat(sent.method()).isEqualTo("POST");
        JsonNode body = sent.json();
        assertThat(body.get("description").asText()).isEqualTo("Pedido #1001 - Pijama Party");
        assertThat(body.get("amount").decimalValue()).isEqualByComparingTo("21000.50");
        assertThat(body.get("currency").asText()).isEqualTo("ARS");
        assertThat(body.get("cc_code").asText()).isEqualTo("1CSI");
        assertThat(body.get("processor_code").asText()).isEqualTo("P-123");
        assertThat(body.get("external_intention_id").asText()).isEqualTo("1001-2");
        assertThat(body.get("webhook_notification").asText())
            .isEqualTo("https://pijamaparty.example/api/payments/modo/webhook");
    }

    @Test
    void givenAConfirmedOrder_whenTheCheckoutIsCreated_thenNoBuyerDataGoesToMODO() {
        subject.createCheckout(REQUEST, ATTEMPT);

        List<String> fields = new ArrayList<>();
        stub.requestsTo(PAYMENT_REQUESTS).getLast().json().fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("description", "amount", "currency", "cc_code", "processor_code",
            "external_intention_id", "expiration_date", "webhook_notification");
        assertThat(stub.requestsTo(PAYMENT_REQUESTS).getLast().body()).doesNotContain("Ana", "ana@example.com");
    }

    @Test
    void givenAReservationWithThirtyMinutesLeft_whenTheCheckoutIsCreated_thenItExpiresInTenMinutesInArgentineTime() {
        PaymentGateway.Outcome outcome = subject.createCheckout(REQUEST, ATTEMPT);

        // 15:10 UTC is 12:10 in Argentina
        assertThat(stub.requestsTo(PAYMENT_REQUESTS).getLast().json().get("expiration_date").asText())
            .isEqualTo("2026-10-12T12:10:00");
        assertThat(((PaymentGateway.Outcome.Created) outcome).checkout().expiresAt())
            .contains(NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    void givenAReservationWithTwoMinutesLeft_whenTheCheckoutIsCreated_thenItExpiresAtMODOsMinimumOfFiveMinutes() {
        var ending = new CheckoutRequest(REQUEST.orderId(), 1001, REQUEST.amount(), PaymentProvider.MODO,
            REQUEST.payer(), NOW.plus(Duration.ofMinutes(2)));

        PaymentGateway.Outcome outcome = subject.createCheckout(ending, ATTEMPT);

        assertThat(stub.requestsTo(PAYMENT_REQUESTS).getLast().json().get("expiration_date").asText())
            .isEqualTo("2026-10-12T12:05:00");
        assertThat(((PaymentGateway.Outcome.Created) outcome).checkout().expiresAt())
            .contains(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void givenAConfirmedOrder_whenTheCheckoutIsCreated_thenItIsMODOsSiteWithTheRequestAndTheStoresResultPage() {
        PaymentGateway.Outcome outcome = subject.createCheckout(REQUEST, ATTEMPT);

        assertThat(outcome).isEqualTo(new PaymentGateway.Outcome.Created(new Checkout("pr-1", URI.create(
            "https://site.preprod.modo.com.ar/checkout?id=pr-1"
                + "&callbackSuccess=https%3A%2F%2Fpijamaparty.example%2Fcheckout%2Fresult%3Fstatus%3Dapproved"
                + "&callback=https%3A%2F%2Fpijamaparty.example%2Fcheckout%2Fresult%3Fstatus%3Dfailure"),
            Optional.of(NOW.plus(Duration.ofMinutes(10))))));
    }

    @Test
    void givenAConfirmedOrder_whenTheCheckoutIsCreated_thenItCarriesTheTokenAndTheMerchantsName() {
        subject.createCheckout(REQUEST, ATTEMPT);

        ModoStub.Request token = stub.requestsTo(TOKENS).getFirst();
        assertThat(token.json().get("username").asText()).isEqualTo(ModoStub.USERNAME);
        assertThat(token.userAgent()).isEqualTo("PijamaParty");
        ModoStub.Request sent = stub.requestsTo(PAYMENT_REQUESTS).getLast();
        assertThat(sent.authorization()).isEqualTo("Bearer token-1");
        assertThat(sent.userAgent()).isEqualTo("PijamaParty");
    }

    @Test
    void givenATokenInForce_whenAnotherCheckoutIsCreated_thenTheSameTokenIsUsedWithoutAskingAgain() {
        subject.createCheckout(REQUEST, ATTEMPT);

        subject.createCheckout(REQUEST, CheckoutAttempt.of(1001, 2));

        assertThat(stub.requestsTo(TOKENS)).hasSize(1);
        assertThat(stub.requestsTo(PAYMENT_REQUESTS)).extracting(ModoStub.Request::authorization)
            .containsOnly("Bearer token-1");
    }

    @Test
    void givenATokenMODONoLongerAccepts_whenTheCheckoutIsCreated_thenANewTokenIsAskedOnceAndTheRequestRepeated() {
        subject.createCheckout(REQUEST, ATTEMPT);
        stub.revokeTokens();

        PaymentGateway.Outcome outcome = subject.createCheckout(REQUEST, CheckoutAttempt.of(1001, 2));

        assertThat(outcome).isInstanceOf(PaymentGateway.Outcome.Created.class);
        assertThat(stub.requestsTo(TOKENS)).hasSize(2);
        assertThat(stub.requestsTo(PAYMENT_REQUESTS).getLast().authorization()).isEqualTo("Bearer token-2");
    }

    @Test
    void givenMODORefusingEveryToken_whenTheCheckoutIsCreated_thenItIsUnavailableAfterOneRenewal() {
        subject.createCheckout(REQUEST, ATTEMPT);
        stub.revokeTokens();
        stub.answerTokens(200, "{\"access_token\":\"not-accepted\",\"expires_in\":604800}");

        PaymentGateway.Outcome outcome = subject.createCheckout(REQUEST, CheckoutAttempt.of(1001, 2));

        assertThat(outcome).isInstanceOf(PaymentGateway.Outcome.Unavailable.class);
        assertThat(stub.requestsTo(TOKENS)).hasSize(2);
        assertThat(stub.requestsTo(PAYMENT_REQUESTS)).hasSize(3);
    }

    @Test
    void givenMODORefusingTheCredentials_whenTheCheckoutIsCreated_thenItIsUnavailableWithoutAPaymentRequest() {
        ModoGateway wrongCredentials = gateway(properties(true, "P-123", Duration.ofSeconds(2)), "someone", "wrong");

        assertThat(wrongCredentials.createCheckout(REQUEST, ATTEMPT))
            .isInstanceOf(PaymentGateway.Outcome.Unavailable.class);
        assertThat(stub.requestsTo(PAYMENT_REQUESTS)).isEmpty();
    }

    @Test
    void givenNoCredentialsOrNoProcessorCode_whenTheCheckoutIsCreated_thenItIsUnavailableWithoutCallingMODO() {
        ModoGateway withoutCredentials = gateway(properties(true, "P-123", Duration.ofSeconds(2)), "", "");
        ModoGateway withoutProcessor = gateway(properties(true, "", Duration.ofSeconds(2)), ModoStub.USERNAME,
            ModoStub.PASSWORD);

        assertThat(withoutCredentials.createCheckout(REQUEST, ATTEMPT))
            .isInstanceOf(PaymentGateway.Outcome.Unavailable.class);
        assertThat(withoutProcessor.createCheckout(REQUEST, ATTEMPT))
            .isInstanceOf(PaymentGateway.Outcome.Unavailable.class);
        assertThat(stub.requests()).isEmpty();
    }

    @Test
    void givenTheConfiguration_whenAskedWhetherBuyersMayChooseMODO_thenItIsWhatItSays() {
        assertThat(subject.enabled()).isTrue();
        assertThat(gateway(properties(false, "P-123", Duration.ofSeconds(2)), ModoStub.USERNAME, ModoStub.PASSWORD)
            .enabled()).isFalse();
    }

    @Test
    void givenMODORefusesTheRequest_whenTheCheckoutIsCreated_thenItIsUnavailable() {
        stub.answerPaymentRequests(400, "{\"message\":\"invalid processor_code\"}");

        assertThat(subject.createCheckout(REQUEST, ATTEMPT)).isInstanceOf(PaymentGateway.Outcome.Unavailable.class);
    }

    @Test
    void givenMODOTakesLongerThanTheReadTimeout_whenTheCheckoutIsCreated_thenItIsUnavailable() {
        ModoGateway impatient = gateway(properties(true, "P-123", Duration.ofMillis(200)), ModoStub.USERNAME,
            ModoStub.PASSWORD);
        stub.delay(Duration.ofSeconds(2));

        assertThat(impatient.createCheckout(REQUEST, ATTEMPT)).isInstanceOf(PaymentGateway.Outcome.Unavailable.class);
    }

    @Test
    void givenMODOIsUnreachable_whenTheCheckoutIsCreated_thenItIsUnavailable() {
        stub.stop();

        assertThat(subject.createCheckout(REQUEST, ATTEMPT)).isInstanceOf(PaymentGateway.Outcome.Unavailable.class);
    }

    @Test
    void givenAnAnswerWithoutAnId_whenTheCheckoutIsCreated_thenItIsUnavailable() {
        stub.answerPaymentRequests(201, "{\"qr\":\"0002\"}");

        assertThat(subject.createCheckout(REQUEST, ATTEMPT)).isInstanceOf(PaymentGateway.Outcome.Unavailable.class);
    }

    @Test
    void givenAnAnswerThatIsNotJson_whenTheCheckoutIsCreated_thenItIsUnavailable() {
        stub.answerPaymentRequests(201, "<html>maintenance</html>");

        assertThat(subject.createCheckout(REQUEST, ATTEMPT)).isInstanceOf(PaymentGateway.Outcome.Unavailable.class);
    }

    private ModoProperties properties(boolean enabled, String processorCode, Duration readTimeout) {
        return new ModoProperties(enabled, false, stub.baseUrl(), CHECKOUT_URL, processorCode, "1CSI", "PijamaParty",
            Duration.ofSeconds(1), readTimeout);
    }

    private ModoGateway gateway(ModoProperties properties, String username, String password) {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var client = ModoRestClients.create(properties);
        return new ModoGateway(properties, client, new ModoAccessTokens(client, username, password, clock),
            ModoConfiguration.configured(properties, username, password), PUBLIC_URL, messages, clock);
    }
}
