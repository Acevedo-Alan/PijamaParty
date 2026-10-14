package com.pijamaparty.store.payments.adapter.out.mercadopago;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pijamaparty.store.payments.api.CheckoutRequest;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.PaymentGateway;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.platform.config.TimeConfiguration;
import com.pijamaparty.store.platform.domain.Money;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * UC-14: MercadoPago Checkout Pro. The checkout is a preference, created with {@code POST /checkout/preferences} and an
 * {@code X-Idempotency-Key} derived from the order number, so a retry never creates a second one (RNF-20.8, T-E3-17).
 * Anti-corruption layer: MercadoPago's JSON never leaves this class. Its own HTTP client with the RNF-21 timeouts and
 * no retries (a creation, not a read). Logs carry the order number and the HTTP status only: never the payer or the
 * provider's answer (R-05).
 */
class MercadoPagoGateway implements PaymentGateway {

    static final String PREFERENCES_PATH = "/checkout/preferences";
    /** RNF-20.8: the name MercadoPago's own SDKs send on every {@code POST}. */
    static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";
    static final String RESULT_PATH = "/checkout/result";
    /** UC-15: {@code source_news=webhooks} asks for the signed Webhooks notifications only, not the unsigned IPN ones. */
    static final String WEBHOOK_PATH = "/api/payments/mercadopago/webhook?source_news=webhooks";

    /** UC-14: only immediate payment methods; cash slips and ATM payments would hold the stock for days. */
    static final List<String> EXCLUDED_PAYMENT_TYPES = List.of("ticket", "atm");

    private static final Locale CUSTOMER_LOCALE = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter EXPIRATION =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(TimeConfiguration.STORE_ZONE);

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoGateway.class);

    private final RestClient client;
    private final boolean configured;
    private final String publicUrl;
    private final MessageSource messages;

    MercadoPagoGateway(MercadoPagoProperties properties, String accessToken, URI publicUrl, MessageSource messages) {
        this.client = MercadoPagoRestClients.create(properties, accessToken);
        this.configured = !accessToken.isBlank();
        this.publicUrl = publicUrl.toString().replaceAll("/+$", "");
        this.messages = messages;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MERCADOPAGO;
    }

    @Override
    public Outcome createCheckout(CheckoutRequest request) {
        if (!configured) {
            log.warn("MercadoPago has no access token (store.payments.mercadopago.access-token): order {} has no "
                + "checkout", request.orderNumber());
            return new Outcome.Unavailable();
        }
        PreferenceResponse response;
        try {
            response = client.post()
                .uri(PREFERENCES_PATH)
                .header(IDEMPOTENCY_KEY, idempotencyKeyOf(request.orderNumber()))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(preferenceOf(request))
                .retrieve()
                .body(PreferenceResponse.class);
        } catch (RestClientResponseException refused) {
            log.warn("MercadoPago refused the checkout of order {}: HTTP {}", request.orderNumber(),
                refused.getStatusCode().value());
            return new Outcome.Unavailable();
        } catch (RestClientException unreachable) {
            log.warn("MercadoPago did not create the checkout of order {}: {}", request.orderNumber(),
                unreachable.getClass().getSimpleName());
            return new Outcome.Unavailable();
        }
        return checkoutOf(response)
            .<Outcome>map(Outcome.Created::new)
            .orElseGet(() -> {
                log.warn("MercadoPago answered the checkout of order {} without an id or an https init_point",
                    request.orderNumber());
                return new Outcome.Unavailable();
            });
    }

    private Preference preferenceOf(CheckoutRequest request) {
        String number = Long.toString(request.orderNumber());
        String title = messages.getMessage("payments.checkout.title", new Object[] {number}, CUSTOMER_LOCALE);
        String result = publicUrl + RESULT_PATH;
        return new Preference(
            List.of(new Item(number, title, 1, pesos(request.amount()), Money.CURRENCY)),
            new Payer(request.payer().name(), request.payer().email()),
            number,
            new BackUrls(result, result, result),
            "approved",
            publicUrl + WEBHOOK_PATH,
            new PaymentMethods(EXCLUDED_PAYMENT_TYPES.stream().map(PaymentType::new).toList()),
            true,
            EXPIRATION.format(request.expiresAt()));
    }

    /**
     * RNF-20.8: the same order always sends the same key, a UUID as MercadoPago asks for; the order number never
     * repeats (RNF-20.6).
     */
    static String idempotencyKeyOf(long orderNumber) {
        return UUID.nameUUIDFromBytes(("pijamaparty:checkout:" + orderNumber).getBytes(StandardCharsets.UTF_8))
            .toString();
    }

    /** MercadoPago takes the price in pesos with decimals; the cents make it exact (R-01). */
    private static BigDecimal pesos(Money amount) {
        return BigDecimal.valueOf(amount.cents(), 2);
    }

    private static Optional<Checkout> checkoutOf(PreferenceResponse response) {
        if (response == null || response.id() == null || response.initPoint() == null) {
            return Optional.empty();
        }
        URI url;
        try {
            url = new URI(response.initPoint());
        } catch (URISyntaxException malformed) {
            return Optional.empty();
        }
        return Checkout.isValid(response.id(), url)
            ? Optional.of(new Checkout(response.id(), url))
            : Optional.empty();
    }

    record Preference(List<Item> items, Payer payer, @JsonProperty("external_reference") String externalReference,
                      @JsonProperty("back_urls") BackUrls backUrls, @JsonProperty("auto_return") String autoReturn,
                      @JsonProperty("notification_url") String notificationUrl,
                      @JsonProperty("payment_methods") PaymentMethods paymentMethods, boolean expires,
                      @JsonProperty("expiration_date_to") String expirationDateTo) {}

    record Item(String id, String title, int quantity, @JsonProperty("unit_price") BigDecimal unitPrice,
                @JsonProperty("currency_id") String currencyId) {}

    record Payer(String name, String email) {

        @Override
        public String toString() {
            return "Payer[***]";
        }
    }

    record BackUrls(String success, String failure, String pending) {}

    record PaymentMethods(@JsonProperty("excluded_payment_types") List<PaymentType> excludedPaymentTypes) {}

    record PaymentType(String id) {}

    record PreferenceResponse(String id, @JsonProperty("init_point") String initPoint) {}
}
