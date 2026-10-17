package com.pijamaparty.store.accounts.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * UC-20: one address of a customer's {@link AddressBook}, which decides whether it is the primary. An order keeps its
 * own copy of the address (R-04), so nothing here reaches an order. Personal data (R-05): {@code toString()} never
 * prints it.
 */
public record Address(AddressId id, AddressDetails details, boolean primary, Instant createdAt, Instant updatedAt) {

    public Address {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    Address with(AddressDetails newDetails, boolean newPrimary, Instant at) {
        if (newDetails.equals(details) && newPrimary == primary) {
            return this;
        }
        return new Address(id, newDetails, newPrimary, createdAt, at);
    }

    @Override
    public String toString() {
        return "Address[" + id + (primary ? ", primary" : "") + "]";
    }
}
