package com.pijamaparty.store.payments.domain;

import java.net.URI;
import java.util.Objects;

/**
 * UC-14: the provider's checkout of a payment (a MercadoPago preference): its id at the provider and the page where
 * the buyer pays, always over HTTPS. The limits are those of the {@code payment} table (V15).
 */
public record Checkout(String id, URI url) {

    public static final int MAX_ID_LENGTH = 200;
    public static final int MAX_URL_LENGTH = 2000;

    public Checkout {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(url, "url");
        if (!isValid(id, url)) {
            throw new IllegalArgumentException("a checkout needs an id of 1 to " + MAX_ID_LENGTH
                + " characters and an absolute https URL of at most " + MAX_URL_LENGTH + " characters");
        }
    }

    /** Whether a provider's answer makes a checkout, so adapters can reject it without catching. */
    public static boolean isValid(String id, URI url) {
        return id != null && !id.isBlank() && id.length() <= MAX_ID_LENGTH
            && url != null && url.isAbsolute() && "https".equals(url.getScheme()) && url.getHost() != null
            && url.toString().length() <= MAX_URL_LENGTH;
    }
}
