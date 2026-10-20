package com.pijamaparty.store.ordering.domain;

import java.util.Objects;

/**
 * UC-13: who buys, as the order keeps it (R-04), already normalized by {@link CheckoutForm}. Personal data (R-05):
 * {@code toString()} never prints it.
 *
 * @param firstName as typed, trimmed; may hold several words ("María José", D-13)
 * @param lastName as typed, trimmed
 * @param email trimmed and in lower case
 * @param phone digits only, 8 to 15
 * @param nationalId the DNI, digits only, 7 or 8
 */
public record BuyerDetails(String firstName, String lastName, String email, String phone, String nationalId) {

    public BuyerDetails {
        Objects.requireNonNull(firstName, "firstName");
        Objects.requireNonNull(lastName, "lastName");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(phone, "phone");
        Objects.requireNonNull(nationalId, "nationalId");
    }

    @Override
    public String toString() {
        return "BuyerDetails[***]";
    }
}
