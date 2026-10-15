package com.pijamaparty.store.payments.domain;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-14: the provider's checkout of a payment (a MercadoPago preference, a MODO payment request): its id at the
 * provider, the page where the buyer pays, always over HTTPS, and when the provider stops accepting it. The limits are
 * those of the {@code payment} and {@code payment_checkout} tables (V15, V27).
 *
 * @param expiresAt empty for a checkout that lasts as long as the reservation (MercadoPago)
 */
public record Checkout(String id, URI url, Optional<Instant> expiresAt) {

    public static final int MAX_ID_LENGTH = 200;
    public static final int MAX_URL_LENGTH = 2000;

    /** T-E3b-02: a checkout about to expire is not handed to the buyer, who would not finish paying in time. */
    public static final Duration MIN_TIME_LEFT = Duration.ofMinutes(1);

    public Checkout {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!isValid(id, url)) {
            throw new IllegalArgumentException("a checkout needs an id of 1 to " + MAX_ID_LENGTH
                + " characters and an absolute https URL of at most " + MAX_URL_LENGTH + " characters");
        }
    }

    /** A checkout that lasts as long as the reservation. */
    public Checkout(String id, URI url) {
        this(id, url, Optional.empty());
    }

    /** Whether a provider's answer makes a checkout, so adapters can reject it without catching. */
    public static boolean isValid(String id, URI url) {
        return id != null && !id.isBlank() && id.length() <= MAX_ID_LENGTH
            && url != null && url.isAbsolute() && "https".equals(url.getScheme()) && url.getHost() != null
            && url.toString().length() <= MAX_URL_LENGTH;
    }

    /** Whether the buyer can still be sent to it: it does not expire, or expires more than a minute after now. */
    public boolean usableAt(Instant now) {
        return expiresAt.map(expiry -> expiry.isAfter(now.plus(MIN_TIME_LEFT))).orElse(true);
    }
}
