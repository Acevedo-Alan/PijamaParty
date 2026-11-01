package com.pijamaparty.store.payments.adapter.out.mercadopago;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pijamaparty.store.payments.api.CardPaymentRequest;
import com.pijamaparty.store.payments.api.CardRejection;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.CardCharges;
import com.pijamaparty.store.platform.domain.Money;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
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
 * UC-14 (T-E3-29): MercadoPago's card payments on the store's page. The browser's card form (Card Payment Brick) turns
 * the card into a single-use token with the public key; this charges it with {@code POST /v1/payments} for the order
 * total, with an {@code X-Idempotency-Key} derived from the order number and the buyer's key of the submit, so a
 * repeated submit never charges twice (RNF-20). The {@code external_reference} is the order number, by which UC-15 finds
 * the payment. Anti-corruption layer: MercadoPago's JSON never leaves this class. Its own HTTP client with the RNF-21
 * timeouts and no retries (a creation, not a read). Logs carry the order number and the HTTP status only: never the
 * card, the payer or the provider's answer (R-05).
 */
class MercadoPagoCardCharges implements CardCharges {

    static final String PAYMENTS_PATH = "/v1/payments";
    /** RNF-20.8: the name MercadoPago's own SDKs send on every {@code POST}. */
    static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";
    /** UC-15: {@code source_news=webhooks} asks for the signed Webhooks notifications only, not the unsigned IPN ones. */
    static final String WEBHOOK_PATH = "/api/payments/mercadopago/webhook?source_news=webhooks";

    /** MercadoPago's {@code status_detail} of a rejected card, in the store's words. */
    private static final Map<String, CardRejection> REJECTIONS = Map.ofEntries(
        Map.entry("cc_rejected_insufficient_amount", CardRejection.INSUFFICIENT_FUNDS),
        Map.entry("cc_rejected_bad_filled_security_code", CardRejection.BAD_SECURITY_CODE),
        Map.entry("cc_rejected_bad_filled_date", CardRejection.BAD_EXPIRY_DATE),
        Map.entry("cc_rejected_bad_filled_card_number", CardRejection.BAD_CARD_DATA),
        Map.entry("cc_rejected_bad_filled_other", CardRejection.BAD_CARD_DATA),
        Map.entry("cc_rejected_call_for_authorize", CardRejection.CALL_FOR_AUTHORIZE),
        Map.entry("cc_rejected_card_disabled", CardRejection.CARD_DISABLED),
        Map.entry("cc_rejected_duplicated_payment", CardRejection.DUPLICATED),
        Map.entry("cc_rejected_max_attempts", CardRejection.MAX_ATTEMPTS),
        Map.entry("cc_rejected_high_risk", CardRejection.HIGH_RISK),
        Map.entry("rejected_high_risk", CardRejection.HIGH_RISK),
        Map.entry("cc_rejected_blacklist", CardRejection.HIGH_RISK));

    private static final Locale CUSTOMER_LOCALE = Locale.forLanguageTag("es-AR");

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoCardCharges.class);

    private final RestClient client;
    private final boolean configured;
    private final Optional<String> publicKey;
    private final String publicUrl;
    private final MessageSource messages;

    MercadoPagoCardCharges(MercadoPagoProperties properties, String accessToken, String publicKey, URI publicUrl,
                           MessageSource messages) {
        this.client = MercadoPagoRestClients.create(properties, accessToken);
        this.configured = !accessToken.isBlank();
        this.publicKey = publicKey.isBlank() ? Optional.empty() : Optional.of(publicKey.strip());
        this.publicUrl = publicUrl.toString().replaceAll("/+$", "");
        this.messages = messages;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MERCADOPAGO;
    }

    @Override
    public Optional<String> publicKey() {
        return publicKey;
    }

    @Override
    public Charge charge(CardCharge charge) {
        if (!configured) {
            log.warn("MercadoPago has no access token (store.payments.mercadopago.access-token): order {} was not "
                + "charged", charge.orderNumber());
            return new Charge.Unavailable();
        }
        PaymentResponse response;
        try {
            response = client.post()
                .uri(PAYMENTS_PATH)
                .header(IDEMPOTENCY_KEY, idempotencyKeyOf(charge.orderNumber(), charge.idempotencyKey()))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(paymentOf(charge))
                .retrieve()
                .body(PaymentResponse.class);
        } catch (RestClientResponseException failed) {
            int status = failed.getStatusCode().value();
            log.warn("MercadoPago did not charge the card of order {}: HTTP {}", charge.orderNumber(), status);
            return failed.getStatusCode().is4xxClientError() ? new Charge.Refused() : new Charge.Unavailable();
        } catch (RestClientException unreachable) {
            log.warn("MercadoPago did not charge the card of order {}: {}", charge.orderNumber(),
                unreachable.getClass().getSimpleName());
            return new Charge.Unavailable();
        }
        if (response == null || response.id() == null || response.status() == null) {
            log.warn("MercadoPago answered the card payment of order {} without an id or a status",
                charge.orderNumber());
            return new Charge.Unavailable();
        }
        ChargeStatus status = statusOf(response.status());
        Optional<CardRejection> rejection = status == ChargeStatus.REJECTED
            ? Optional.of(REJECTIONS.getOrDefault(String.valueOf(response.statusDetail()), CardRejection.OTHER))
            : Optional.empty();
        return new Charge.Charged(response.id().toPlainString(), status, rejection);
    }

    private Payment paymentOf(CardCharge charge) {
        String number = Long.toString(charge.orderNumber());
        CardPaymentRequest.Card card = charge.card();
        return new Payment(
            pesos(charge.amount()),
            card.token(),
            messages.getMessage("payments.checkout.title", new Object[] {number}, CUSTOMER_LOCALE),
            card.installments(),
            card.paymentMethodId(),
            card.issuerId().orElse(null),
            new Payer(charge.payerEmail(), new Identification(card.identificationType(), card.identificationNumber())),
            number,
            publicUrl + WEBHOOK_PATH);
    }

    /**
     * RNF-20: the same submit always sends the same key (a UUID, as MercadoPago asks for); another submit of the same
     * order, another key. The order number never repeats (RNF-20.6).
     */
    static String idempotencyKeyOf(long orderNumber, UUID submitKey) {
        return UUID.nameUUIDFromBytes(("pijamaparty:card:" + orderNumber + ":" + submitKey)
            .getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** {@code approved}; {@code rejected} or {@code cancelled}; the rest ({@code in_process}, {@code pending}) waits. */
    private static ChargeStatus statusOf(String status) {
        return switch (status) {
            case "approved" -> ChargeStatus.APPROVED;
            case "rejected", "cancelled" -> ChargeStatus.REJECTED;
            default -> ChargeStatus.IN_PROCESS;
        };
    }

    /** MercadoPago takes the amount in pesos with decimals; the cents make it exact (R-01). */
    private static BigDecimal pesos(Money amount) {
        return BigDecimal.valueOf(amount.cents(), 2);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Payment(@JsonProperty("transaction_amount") BigDecimal transactionAmount, String token,
                   String description, int installments, @JsonProperty("payment_method_id") String paymentMethodId,
                   @JsonProperty("issuer_id") String issuerId, Payer payer,
                   @JsonProperty("external_reference") String externalReference,
                   @JsonProperty("notification_url") String notificationUrl) {

        @Override
        public String toString() {
            return "Payment[***]";
        }
    }

    record Payer(String email, Identification identification) {

        @Override
        public String toString() {
            return "Payer[***]";
        }
    }

    record Identification(String type, String number) {

        @Override
        public String toString() {
            return "Identification[***]";
        }
    }

    record PaymentResponse(BigDecimal id, String status, @JsonProperty("status_detail") String statusDetail) {}
}
