package com.pijamaparty.store.payments.adapter.out.modo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.PaymentVerifier;
import com.pijamaparty.store.payments.domain.PaymentReference;
import com.pijamaparty.store.payments.domain.VerifiedPayment;
import com.pijamaparty.store.payments.domain.VerifiedStatus;
import com.pijamaparty.store.platform.domain.Money;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * UC-15 (T-E3b-03): asks MODO for a payment request with {@code GET /v2/payment-requests/{id}/data} (R-06), with the
 * gateway's client and token. MODO answers only for payments approved, rejected or refunded, so a {@code 404} is a
 * payment request nobody paid (assumed; to verify in T-E3b-05). MODO does not return the
 * {@code external_intention_id}, so the payment refers to its checkout: the payment request id. Anti-corruption
 * layer: MODO's JSON never leaves this class, and only the status and the amount are read; the answer carries the
 * payer's name, national ID and card digits, which are never logged (R-05). Logs carry the payment request id and
 * the HTTP status only. No retries: the reconciliation asks again a minute later.
 */
class ModoPaymentVerifier implements PaymentVerifier {

    static final String DATA_PATH = "/v2/payment-requests/{id}/data";

    /** The final statuses of a payment request; an unknown one makes the answer unusable. */
    private static final Map<String, VerifiedStatus> STATUSES = Map.of(
        "ACCEPTED", VerifiedStatus.APPROVED,
        "REJECTED", VerifiedStatus.REJECTED,
        "REFUNDED", VerifiedStatus.OTHER,
        "PARTIAL_REFUND", VerifiedStatus.OTHER);

    private static final Logger log = LoggerFactory.getLogger(ModoPaymentVerifier.class);

    private final RestClient client;
    private final ModoAccessTokens tokens;
    private final boolean configured;

    ModoPaymentVerifier(RestClient client, ModoAccessTokens tokens, boolean configured) {
        this.client = client;
        this.tokens = tokens;
        this.configured = configured;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MODO;
    }

    @Override
    public boolean verifiesByCheckout() {
        return true;
    }

    @Override
    public Verification verify(String externalPaymentId) {
        if (!ModoPaymentRequestIds.isValid(externalPaymentId)) {
            return new Verification.NotFound();
        }
        if (!configured) {
            log.warn("MODO has no credentials or processor code (store.payments.modo.*): payment request {} cannot "
                + "be verified", externalPaymentId);
            return new Verification.Unavailable();
        }
        Optional<String> token = tokens.current();
        if (token.isEmpty()) {
            log.warn("MODO gave no access token: payment request {} cannot be verified", externalPaymentId);
            return new Verification.Unavailable();
        }
        DataResponse response;
        try {
            response = fetch(externalPaymentId, token.get());
        } catch (RestClientResponseException refused) {
            if (refused.getStatusCode().value() != HttpStatus.UNAUTHORIZED.value()) {
                return refused(externalPaymentId, refused);
            }
            Optional<String> renewed = tokens.renew(token.get());
            if (renewed.isEmpty()) {
                return refused(externalPaymentId, refused);
            }
            try {
                response = fetch(externalPaymentId, renewed.get());
            } catch (RestClientResponseException refusedAgain) {
                return refused(externalPaymentId, refusedAgain);
            } catch (RestClientException unreachable) {
                return unreachable(externalPaymentId, unreachable);
            }
        } catch (RestClientException unreachable) {
            return unreachable(externalPaymentId, unreachable);
        }
        return verifiedOf(externalPaymentId, response)
            .<Verification>map(Verification.Found::new)
            .orElseGet(() -> {
                log.warn("MODO answered payment request {} without its id, a known status or a usable amount",
                    externalPaymentId);
                return new Verification.Unavailable();
            });
    }

    private DataResponse fetch(String id, String token) {
        return client.get()
            .uri(DATA_PATH, id)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(DataResponse.class);
    }

    private static Verification refused(String id, RestClientResponseException refused) {
        if (refused.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
            return new Verification.NotFound();
        }
        log.warn("MODO refused to show payment request {}: HTTP {}", id, refused.getStatusCode().value());
        return new Verification.Unavailable();
    }

    private static Verification unreachable(String id, RestClientException unreachable) {
        log.warn("MODO did not show payment request {}: {}", id, unreachable.getClass().getSimpleName());
        return new Verification.Unavailable();
    }

    private static Optional<VerifiedPayment> verifiedOf(String asked, DataResponse response) {
        if (response == null || !asked.equals(response.id()) || !STATUSES.containsKey(response.status())
            || response.transaction() == null) {
            return Optional.empty();
        }
        String paidRequest = response.transaction().paymentRequestId();
        if (paidRequest != null && !paidRequest.equals(asked)) {
            return Optional.empty();
        }
        return centsOf(response.transaction().amount()).map(amount -> new VerifiedPayment(asked,
            new PaymentReference.ByCheckout(asked), STATUSES.get(response.status()), amount));
    }

    /**
     * Pesos above zero, with at most two significant decimals (MODO writes three, {@code "100.000"}), as exact cents
     * (R-01).
     */
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

    /** The payment request; MODO always charges in pesos. */
    record DataResponse(String id, String status, @JsonProperty("transaction_data") TransactionData transaction) {}

    /** {@code amount} is a string in MODO's examples ({@code "100.000"}); read as a decimal either way. */
    record TransactionData(@JsonProperty("payment_request_id") String paymentRequestId, BigDecimal amount) {}
}
