package com.pijamaparty.store.payments.adapter.out.mercadopago;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pijamaparty.store.payments.api.CardPaymentRequest;
import com.pijamaparty.store.payments.api.CardRejection;
import com.pijamaparty.store.payments.api.CardType;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.CardCharges;
import com.pijamaparty.store.platform.domain.Money;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
 * UC-14 (T-E3-29, T-E3-34): MercadoPago's card payments on the store's page. The browser's card form (Card Payment
 * Brick) turns the card into a single-use token with the public key; this charges it by creating a MercadoPago order
 * ({@code POST /v1/orders}, the Orders API: the sandbox's credentials refuse {@code /v1/payments}) for the order total,
 * with an {@code X-Idempotency-Key} derived from the order number and the buyer's key of the submit, so a repeated
 * submit answers the same order and never charges twice (RNF-20). The {@code external_reference} is the order number, by
 * which UC-15 finds the payment. Anti-corruption layer: MercadoPago's JSON never leaves this class. Its own HTTP client
 * with the RNF-21 timeouts and no retries (a creation, not a read). Logs carry the order number, the HTTP status and
 * MercadoPago's error code only: never the card, the payer or the body (R-05).
 */
class MercadoPagoCardCharges implements CardCharges {

    static final String ORDERS_PATH = "/v1/orders";
    /** RNF-20.8: the name MercadoPago's own SDKs send on every {@code POST}. */
    static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";

    /** What the Orders API answers for a card it refused, with the order in its {@code data}. */
    private static final int PAYMENT_REQUIRED = 402;

    /** The {@code status_detail} of a failed payment of the Orders API, in the store's words; the rest is OTHER. */
    private static final Map<String, CardRejection> REJECTIONS = Map.of(
        "insufficient_amount", CardRejection.INSUFFICIENT_FUNDS,
        "card_insufficient_amount", CardRejection.INSUFFICIENT_FUNDS,
        "bad_filled_card_data", CardRejection.BAD_CARD_DATA,
        "required_call_for_authorize", CardRejection.CALL_FOR_AUTHORIZE,
        "card_disabled", CardRejection.CARD_DISABLED,
        "max_attempts_exceeded", CardRejection.MAX_ATTEMPTS,
        "high_risk", CardRejection.HIGH_RISK);

    private static final Locale CUSTOMER_LOCALE = Locale.forLanguageTag("es-AR");

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoCardCharges.class);

    private final RestClient client;
    private final boolean configured;
    private final Optional<String> publicKey;
    private final MessageSource messages;

    MercadoPagoCardCharges(MercadoPagoProperties properties, String accessToken, String publicKey,
                           MessageSource messages) {
        this.client = MercadoPagoRestClients.create(properties, accessToken);
        this.configured = !accessToken.isBlank();
        this.publicKey = publicKey.isBlank() ? Optional.empty() : Optional.of(publicKey.strip());
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
        OrderResponse order;
        try {
            order = client.post()
                .uri(ORDERS_PATH)
                .header(IDEMPOTENCY_KEY, idempotencyKeyOf(charge.orderNumber(), charge.idempotencyKey()))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(orderOf(charge))
                .retrieve()
                .body(OrderResponse.class);
        } catch (RestClientResponseException failed) {
            return failureOf(charge.orderNumber(), failed);
        } catch (RestClientException unreachable) {
            log.warn("MercadoPago did not charge the card of order {}: {}", charge.orderNumber(),
                unreachable.getClass().getSimpleName());
            return new Charge.Unavailable();
        }
        return chargedOf(charge.orderNumber(), order).orElseGet(() -> {
            log.warn("MercadoPago answered the card payment of order {} without an order id or a status",
                charge.orderNumber());
            return new Charge.Unavailable();
        });
    }

    /** A refused card (402) still brings its order; the credentials, another refusal or a failure do not. */
    private static Charge failureOf(long orderNumber, RestClientResponseException failed) {
        int status = failed.getStatusCode().value();
        ErrorResponse error = bodyOf(failed);
        if (status == PAYMENT_REQUIRED && error != null) {
            Optional<Charge> charged = chargedOf(orderNumber, error.data());
            if (charged.isPresent()) {
                return charged.get();
            }
        }
        String code = error == null ? "none" : error.code();
        if (status == 401 || status == 403) {
            log.error("MercadoPago refused the store's credentials charging order {}: HTTP {}, error {}. Check "
                + "store.payments.mercadopago.access-token", orderNumber, status, code);
            return new Charge.CredentialsRefused();
        }
        log.warn("MercadoPago did not charge the card of order {}: HTTP {}, error {}", orderNumber, status, code);
        return failed.getStatusCode().is4xxClientError() ? new Charge.Refused() : new Charge.Unavailable();
    }

    private static ErrorResponse bodyOf(RestClientResponseException failed) {
        try {
            return failed.getResponseBodyAs(ErrorResponse.class);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    private static Optional<Charge> chargedOf(long orderNumber, OrderResponse order) {
        if (order == null || order.id() == null || order.id().isBlank() || order.status() == null) {
            return Optional.empty();
        }
        ChargeStatus status = statusOf(order.status());
        Optional<CardRejection> rejection = status == ChargeStatus.REJECTED
            ? Optional.of(REJECTIONS.getOrDefault(order.paymentStatusDetail(), CardRejection.OTHER))
            : Optional.empty();
        if (status == ChargeStatus.REJECTED) {
            log.info("MercadoPago refused the card of order {}: {}", orderNumber, order.paymentStatusDetail());
        }
        return Optional.of(new Charge.Charged(order.id(), status, rejection));
    }

    private OrderRequest orderOf(CardCharge charge) {
        String number = Long.toString(charge.orderNumber());
        CardPaymentRequest.Card card = charge.card();
        String amount = pesos(charge.amount());
        var method = new PaymentMethod(card.paymentMethodId(), typeOf(card.type()), card.token(), card.installments(),
            card.issuerId().orElse(null));
        return new OrderRequest(
            "online",
            "automatic",
            amount,
            number,
            messages.getMessage("payments.checkout.title", new Object[] {number}, CUSTOMER_LOCALE),
            new Payer(charge.payerEmail(), new Identification(card.identificationType(), card.identificationNumber())),
            new Transactions(List.of(new Payment(amount, method))));
    }

    private static String typeOf(CardType type) {
        return switch (type) {
            case CREDIT_CARD -> "credit_card";
            case DEBIT_CARD -> "debit_card";
        };
    }

    /**
     * RNF-20: the same submit always sends the same key (a UUID, as MercadoPago asks for); another submit of the same
     * order, another key. The order number never repeats (RNF-20.6).
     */
    static String idempotencyKeyOf(long orderNumber, UUID submitKey) {
        return UUID.nameUUIDFromBytes(("pijamaparty:card:" + orderNumber + ":" + submitKey)
            .getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * The order's status: {@code processed} (approved); {@code failed}, {@code canceled} or {@code expired}; the rest
     * ({@code processing}, {@code action_required}, {@code created}, {@code in_review}) waits for the webhook.
     */
    private static ChargeStatus statusOf(String status) {
        return switch (status) {
            case "processed" -> ChargeStatus.APPROVED;
            case "failed", "canceled", "expired" -> ChargeStatus.REJECTED;
            default -> ChargeStatus.IN_PROCESS;
        };
    }

    /** The Orders API takes amounts as text in pesos with two decimals; the cents make it exact (R-01). */
    private static String pesos(Money amount) {
        return BigDecimal.valueOf(amount.cents(), 2).toPlainString();
    }

    record OrderRequest(String type, @JsonProperty("processing_mode") String processingMode,
                        @JsonProperty("total_amount") String totalAmount,
                        @JsonProperty("external_reference") String externalReference, String description, Payer payer,
                        Transactions transactions) {

        @Override
        public String toString() {
            return "OrderRequest[***]";
        }
    }

    record Transactions(List<Payment> payments) {}

    record Payment(String amount, @JsonProperty("payment_method") PaymentMethod paymentMethod) {

        @Override
        public String toString() {
            return "Payment[***]";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PaymentMethod(String id, String type, String token, int installments,
                         @JsonProperty("issuer_id") String issuerId) {

        @Override
        public String toString() {
            return "PaymentMethod[***]";
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

    record OrderResponse(String id, String status, Transactions transactions) {

        /** The reason of its payment, the one this store creates; empty without one. */
        String paymentStatusDetail() {
            if (transactions == null || transactions.payments() == null || transactions.payments().isEmpty()) {
                return "";
            }
            return String.valueOf(transactions.payments().getFirst().statusDetail());
        }

        record Transactions(List<PaymentStatus> payments) {}

        record PaymentStatus(String id, String status, @JsonProperty("status_detail") String statusDetail) {}
    }

    /** The Orders API's error answer: its errors and, for a refused card, the order. */
    record ErrorResponse(List<Error> errors, OrderResponse data) {

        /** The first error's code, MercadoPago's word for what failed ({@code failed}, {@code unauthorized} ...). */
        String code() {
            return errors == null || errors.isEmpty() ? "none" : String.valueOf(errors.getFirst().code());
        }

        record Error(String code) {}
    }
}
