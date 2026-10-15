package com.pijamaparty.store.payments.adapter.out.modo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pijamaparty.store.payments.api.CheckoutRequest;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.PaymentGateway;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.payments.domain.CheckoutAttempt;
import com.pijamaparty.store.payments.domain.PaymentRequestExpiry;
import com.pijamaparty.store.platform.config.TimeConfiguration;
import com.pijamaparty.store.platform.domain.Money;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * UC-14 (T-E3b-02): MODO's payment button. The checkout is a payment request, created with
 * {@code POST /v2/payment-requests/}; the buyer is redirected to MODO's site, which shows a QR on a desktop and opens
 * the bank app on a phone. A payment request lasts 5 to 10 minutes ({@link PaymentRequestExpiry}) and MODO documents
 * no idempotency header, so each attempt is a new one with its own {@code external_intention_id} (RNF-20.8).
 * Anti-corruption layer: MODO's JSON never leaves this class. No buyer data goes to MODO: its {@code customer}
 * requires the national ID (R-05). Logs carry the order number and the HTTP status only.
 */
class ModoGateway implements PaymentGateway {

    static final String PAYMENT_REQUESTS_PATH = "/v2/payment-requests/";
    static final String CHECKOUT_PATH = "/checkout";
    static final String WEBHOOK_PATH = "/api/payments/modo/webhook";
    static final String RESULT_PATH = "/checkout/result";

    private static final Locale CUSTOMER_LOCALE = Locale.forLanguageTag("es-AR");
    /** MODO's format carries no zone; the store's is assumed until preproduction confirms it (T-E3b-05). */
    private static final DateTimeFormatter EXPIRATION =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(TimeConfiguration.STORE_ZONE);

    private static final Logger log = LoggerFactory.getLogger(ModoGateway.class);

    private final ModoProperties properties;
    private final RestClient client;
    private final ModoAccessTokens tokens;
    private final boolean configured;
    private final String publicUrl;
    private final MessageSource messages;
    private final Clock clock;

    ModoGateway(ModoProperties properties, RestClient client, ModoAccessTokens tokens, boolean configured,
                URI publicUrl, MessageSource messages, Clock clock) {
        this.properties = properties;
        this.client = client;
        this.tokens = tokens;
        this.configured = configured;
        this.publicUrl = publicUrl.toString().replaceAll("/+$", "");
        this.messages = messages;
        this.clock = clock;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MODO;
    }

    @Override
    public boolean enabled() {
        return properties.enabled();
    }

    @Override
    public Outcome createCheckout(CheckoutRequest request, CheckoutAttempt attempt) {
        if (!configured) {
            log.warn("MODO has no credentials or processor code (store.payments.modo.*): order {} has no checkout",
                request.orderNumber());
            return new Outcome.Unavailable();
        }
        Optional<String> token = tokens.current();
        if (token.isEmpty()) {
            log.warn("MODO gave no access token: order {} has no checkout", request.orderNumber());
            return new Outcome.Unavailable();
        }
        Instant expiresAt = PaymentRequestExpiry.of(clock.instant(), request.expiresAt());
        PaymentRequest body = paymentRequestOf(request, attempt, expiresAt);
        PaymentRequestResponse response;
        try {
            response = send(body, token.get());
        } catch (RestClientResponseException refused) {
            if (refused.getStatusCode().value() != HttpStatus.UNAUTHORIZED.value()) {
                return refused(request, refused);
            }
            Optional<String> renewed = tokens.renew(token.get());
            if (renewed.isEmpty()) {
                return refused(request, refused);
            }
            try {
                response = send(body, renewed.get());
            } catch (RestClientResponseException refusedAgain) {
                return refused(request, refusedAgain);
            } catch (RestClientException unreachable) {
                return unreachable(request, unreachable);
            }
        } catch (RestClientException unreachable) {
            return unreachable(request, unreachable);
        }
        return checkoutOf(response, expiresAt)
            .<Outcome>map(Outcome.Created::new)
            .orElseGet(() -> {
                log.warn("MODO answered the checkout of order {} without a usable id", request.orderNumber());
                return new Outcome.Unavailable();
            });
    }

    private PaymentRequestResponse send(PaymentRequest body, String token) {
        return client.post()
            .uri(PAYMENT_REQUESTS_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(PaymentRequestResponse.class);
    }

    private static Outcome refused(CheckoutRequest request, RestClientResponseException refused) {
        log.warn("MODO refused the checkout of order {}: HTTP {}", request.orderNumber(),
            refused.getStatusCode().value());
        return new Outcome.Unavailable();
    }

    private static Outcome unreachable(CheckoutRequest request, RestClientException unreachable) {
        log.warn("MODO did not create the checkout of order {}: {}", request.orderNumber(),
            unreachable.getClass().getSimpleName());
        return new Outcome.Unavailable();
    }

    private PaymentRequest paymentRequestOf(CheckoutRequest request, CheckoutAttempt attempt, Instant expiresAt) {
        String description = messages.getMessage("payments.checkout.title",
            new Object[] {Long.toString(request.orderNumber())}, CUSTOMER_LOCALE);
        return new PaymentRequest(description, pesos(request.amount()), Money.CURRENCY, properties.ccCode(),
            properties.processorCode(), attempt.externalReference(), EXPIRATION.format(expiresAt),
            publicUrl + WEBHOOK_PATH);
    }

    /** MODO takes the amount in pesos with decimals; the cents make it exact (R-01). */
    private static BigDecimal pesos(Money amount) {
        return BigDecimal.valueOf(amount.cents(), 2);
    }

    /**
     * MODO's site with the payment request and where it sends the buyer back: the result page, which reads
     * {@code status}; the tracking token never leaves the store.
     */
    private Optional<Checkout> checkoutOf(PaymentRequestResponse response, Instant expiresAt) {
        if (response == null || response.id() == null) {
            return Optional.empty();
        }
        URI url;
        try {
            url = URI.create(properties.checkoutUrl().toString().replaceAll("/+$", "") + CHECKOUT_PATH
                + "?id=" + encoded(response.id())
                + "&callbackSuccess=" + encoded(publicUrl + RESULT_PATH + "?status=approved")
                + "&callback=" + encoded(publicUrl + RESULT_PATH + "?status=failure"));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
        return Checkout.isValid(response.id(), url)
            ? Optional.of(new Checkout(response.id(), url, Optional.of(expiresAt)))
            : Optional.empty();
    }

    private static String encoded(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    record PaymentRequest(String description, BigDecimal amount, String currency,
                          @JsonProperty("cc_code") String ccCode,
                          @JsonProperty("processor_code") String processorCode,
                          @JsonProperty("external_intention_id") String externalIntentionId,
                          @JsonProperty("expiration_date") String expirationDate,
                          @JsonProperty("webhook_notification") String webhookNotification) {}

    record PaymentRequestResponse(String id) {}
}
