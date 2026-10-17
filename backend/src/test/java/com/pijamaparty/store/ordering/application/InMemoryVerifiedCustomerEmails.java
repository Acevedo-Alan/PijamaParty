package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Fake of {@link VerifiedCustomerEmails}: the customers whose email is verified, and that email. */
public class InMemoryVerifiedCustomerEmails implements VerifiedCustomerEmails {

    private final Map<UserId, String> verified = new HashMap<>();

    /** Test arrangement: a customer whose verified email is {@code email}. */
    public synchronized void verified(UserId customer, String email) {
        verified.put(customer, email);
    }

    @Override
    public synchronized Optional<String> of(UserId customer) {
        return Optional.ofNullable(verified.get(customer));
    }
}
