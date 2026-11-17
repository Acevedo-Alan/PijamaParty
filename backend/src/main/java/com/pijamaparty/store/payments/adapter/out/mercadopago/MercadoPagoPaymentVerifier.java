package com.pijamaparty.store.payments.adapter.out.mercadopago;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.PaymentVerifier;
import com.pijamaparty.store.payments.domain.VerifiedPayment;
import com.pijamaparty.store.payments.domain.VerifiedStatus;
import com.pijamaparty.store.platform.domain.Money;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * UC-15 (T-E3-34): asks MercadoPago for an order of its Orders API with {@code GET /v1/orders/{id}} (R-06); the order's
 * id is the store's provider payment id, one MercadoPago order per card sent. Anti-corruption layer: the order's JSON
 * never leaves this class, and only the fields the store needs are read. No retries: MercadoPago delivers the
 * notification again. Logs carry the order id and the HTTP status only: never the payer or the provider's answer
 * (R-05).
 */
class MercadoPagoPaymentVerifier implements PaymentVerifier {

    static final String ORDERS_PATH = "/v1/orders/{id}";

    /** The Orders API's ids: {@code ORD} and uppercase letters and digits ({@code ORD01…}, {@code ORDTST01…}). */
    private static final Pattern ORDER_ID = Pattern.compile("ORD[A-Z0-9]{1,61}");
    private static final Pattern ORDER_NUMBER = Pattern.compile("[0-9]{1,18}");

    /** The status of a MercadoPago order; {@code processed} is an approval only when accredited. */
    private static final Map<String, VerifiedStatus> STATUSES = Map.of(
        "failed", VerifiedStatus.REJECTED,
        "canceled", VerifiedStatus.REJECTED,
        "expired", VerifiedStatus.REJECTED,
        "created", VerifiedStatus.PENDING,
        "processing", VerifiedStatus.PENDING,
        "action_required", VerifiedStatus.PENDING,
        "in_review", VerifiedStatus.PENDING,
        "refunded", VerifiedStatus.OTHER,
        "charged_back", VerifiedStatus.OTHER);

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoPaymentVerifier.class);

    private final RestClient client;
    private final boolean configured;

    MercadoPagoPaymentVerifier(MercadoPagoProperties properties, String accessToken) {
        this.client = MercadoPagoRestClients.create(properties, accessToken);
        this.configured = !accessToken.isBlank();
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MERCADOPAGO;
    }

    @Override
    public Verification verify(String externalPaymentId) {
        if (!ORDER_ID.matcher(externalPaymentId).matches()) {
            return new Verification.NotFound();
        }
        if (!configured) {
            log.warn("MercadoPago has no access token (store.payments.mercadopago.access-token): order {} cannot be "
                + "verified", externalPaymentId);
            return new Verification.Unavailable();
        }
        OrderResponse response;
        try {
            response = client.get()
                .uri(ORDERS_PATH, externalPaymentId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(OrderResponse.class);
        } catch (RestClientResponseException refused) {
            int status = refused.getStatusCode().value();
            // The Orders API answers 400 for an id it does not know (seen in the sandbox, T-E3-34).
            if (status == HttpStatus.NOT_FOUND.value() || status == HttpStatus.BAD_REQUEST.value()) {
                return new Verification.NotFound();
            }
            log.warn("MercadoPago refused to show order {}: HTTP {}", externalPaymentId, status);
            return new Verification.Unavailable();
        } catch (RestClientException unreachable) {
            log.warn("MercadoPago did not show order {}: {}", externalPaymentId,
                unreachable.getClass().getSimpleName());
            return new Verification.Unavailable();
        }
        return verifiedOf(externalPaymentId, response)
            .<Verification>map(Verification.Found::new)
            .orElseGet(() -> {
                log.warn("MercadoPago answered order {} without its id, a usable external_reference, status, "
                    + "currency or amount", externalPaymentId);
                return new Verification.Unavailable();
            });
    }

    private static Optional<VerifiedPayment> verifiedOf(String asked, OrderResponse response) {
        if (response == null || !asked.equals(response.id()) || response.externalReference() == null
            || !ORDER_NUMBER.matcher(response.externalReference()).matches()
            || !Money.CURRENCY.equals(response.currency())) {
            return Optional.empty();
        }
        Optional<VerifiedStatus> status = statusOf(response.status(), response.statusDetail());
        long orderNumber = Long.parseLong(response.externalReference());
        Optional<Money> amount = centsOf(response.totalAmount());
        if (status.isEmpty() || orderNumber == 0 || amount.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new VerifiedPayment(response.id(), orderNumber, status.get(), amount.get()));
    }

    /**
     * {@code processed} and {@code accredited} is an approval; {@code processed} with another detail
     * ({@code partially_refunded}) is not. An unknown status makes the answer unusable.
     */
    private static Optional<VerifiedStatus> statusOf(String status, String detail) {
        if ("processed".equals(status)) {
            return Optional.of("accredited".equals(detail) ? VerifiedStatus.APPROVED : VerifiedStatus.OTHER);
        }
        return Optional.ofNullable(status == null ? null : STATUSES.get(status));
    }

    /**
     * Pesos with at most two decimals, above zero, as exact cents (R-01). The Orders API writes amounts as text;
     * {@code total_amount} is what the store asked for (whether {@code total_paid_amount} adds the installments'
     * interest is not documented).
     */
    private static Optional<Money> centsOf(String text) {
        if (text == null) {
            return Optional.empty();
        }
        BigDecimal pesos;
        try {
            pesos = new BigDecimal(text);
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
        if (pesos.signum() <= 0 || pesos.stripTrailingZeros().scale() > 2) {
            return Optional.empty();
        }
        try {
            return Optional.of(Money.of(pesos.movePointRight(2).longValueExact()));
        } catch (ArithmeticException tooLarge) {
            return Optional.empty();
        }
    }

    record OrderResponse(String id, String status, @JsonProperty("status_detail") String statusDetail,
                         @JsonProperty("external_reference") String externalReference,
                         @JsonProperty("total_amount") String totalAmount, String currency) {}
}
