package com.pijamaparty.store.ordering.domain;

import java.util.Objects;

/**
 * UC-13: who buys, as the order keeps it (R-04), already normalized by {@link CheckoutForm}. Personal data (R-05):
 * {@code toString()} never prints it.
 *
 * @param email trimmed and in lower case
 * @param phone digits only, 8 to 15
 * @param nationalId the DNI, digits only, 7 or 8
 */
public record BuyerDetails(String name, String email, String phone, String nationalId) {

    public BuyerDetails {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(phone, "phone");
        Objects.requireNonNull(nationalId, "nationalId");
    }

    /**
     * UC-14 (T-E3-08): the tracking link shows only the first word of the buyer's name, since the link can be
     * forwarded. A name is never blank (UC-13).
     */
    public static String firstNameOf(String name) {
        return name.strip().split("\\s+", 2)[0];
    }

    @Override
    public String toString() {
        return "BuyerDetails[***]";
    }
}
