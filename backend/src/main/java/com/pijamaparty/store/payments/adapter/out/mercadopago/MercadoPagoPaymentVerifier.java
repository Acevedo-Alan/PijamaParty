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
 * UC-15: asks MercadoPago for a payment with {@code GET /v1/payments/{id}} (R-06). Anti-corruption layer: the payment's
 * JSON never leaves this class, and only the fields the store needs are read. No retries: MercadoPago delivers the
 * notification again. Logs carry the payment id and the HTTP status only: never the payer or the provider's answer
 * (R-05).
 */
class MercadoPagoPaymentVerifier implements PaymentVerifier {

    static final String PAYMENTS_PATH = "/v1/payments/{id}";

    private static final Pattern PAYMENT_ID = Pattern.compile("[0-9]{1,20}");
    private static final Pattern ORDER_NUMBER = Pattern.compile("[0-9]{1,18}");

    /** MercadoPago's payment statuses; an unknown one makes the answer unusable. */
    private static final Map<String, VerifiedStatus> STATUSES = Map.of(
        "approved", VerifiedStatus.APPROVED,
        "rejected", VerifiedStatus.REJECTED,
        "cancelled", VerifiedStatus.REJECTED,
        "pending", VerifiedStatus.PENDING,
        "in_process", VerifiedStatus.PENDING,
        "authorized", VerifiedStatus.PENDING,
        "in_mediation", VerifiedStatus.PENDING,
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
        if (!PAYMENT_ID.matcher(externalPaymentId).matches()) {
            return new Verification.NotFound();
        }
        if (!configured) {
            log.warn("MercadoPago has no access token (store.payments.mercadopago.access-token): payment {} cannot be "
                + "verified", externalPaymentId);
            return new Verification.Unavailable();
        }
        PaymentResponse response;
        try {
            response = client.get()
                .uri(PAYMENTS_PATH, externalPaymentId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(PaymentResponse.class);
        } catch (RestClientResponseException refused) {
            if (refused.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return new Verification.NotFound();
            }
            log.warn("MercadoPago refused to show payment {}: HTTP {}", externalPaymentId,
                refused.getStatusCode().value());
            return new Verification.Unavailable();
        } catch (RestClientException unreachable) {
            log.warn("MercadoPago did not show payment {}: {}", externalPaymentId,
                unreachable.getClass().getSimpleName());
            return new Verification.Unavailable();
        }
        return verifiedOf(externalPaymentId, response)
            .<Verification>map(Verification.Found::new)
            .orElseGet(() -> {
                log.warn("MercadoPago answered payment {} without its id, a usable external_reference, status, "
                    + "currency or amount", externalPaymentId);
                return new Verification.Unavailable();
            });
    }

    private static Optional<VerifiedPayment> verifiedOf(String asked, PaymentResponse response) {
        if (response == null || response.id() == null || response.externalReference() == null
            || !ORDER_NUMBER.matcher(response.externalReference()).matches()
            || !STATUSES.containsKey(response.status()) || !Money.CURRENCY.equals(response.currencyId())) {
            return Optional.empty();
        }
        String id = response.id().toPlainString();
        long orderNumber = Long.parseLong(response.externalReference());
        Optional<Money> amount = centsOf(response.transactionAmount());
        if (!id.equals(asked) || orderNumber == 0 || amount.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new VerifiedPayment(id, orderNumber, STATUSES.get(response.status()), amount.get()));
    }

    /** Pesos with at most two decimals, above zero, as exact cents (R-01). */
    private static Optional<Money> centsOf(BigDecimal pesos) {
        if (pesos == null || pesos.signum() <= 0 || pesos.stripTrailingZeros().scale() > 2) {
            return Optional.empty();
        }
        try {
            return Optional.of(Money.of(pesos.movePointRight(2).longValueExact()));
        } catch (ArithmeticException tooLarge) {
            return Optional.empty();
        }
    }

    /** {@code id} is a number in MercadoPago's JSON; read as a decimal so a large one is not rounded. */
    record PaymentResponse(BigDecimal id, String status,
                           @JsonProperty("external_reference") String externalReference,
                           @JsonProperty("transaction_amount") BigDecimal transactionAmount,
                           @JsonProperty("currency_id") String currencyId) {}
}
