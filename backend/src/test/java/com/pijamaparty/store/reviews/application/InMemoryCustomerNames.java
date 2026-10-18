package com.pijamaparty.store.reviews.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Fake of {@link CustomerNames}: a customer has an account once {@link #add} names it. */
public class InMemoryCustomerNames implements CustomerNames {

    private final Map<UserId, String> names = new ConcurrentHashMap<>();

    public void add(UserId customer, String name) {
        names.put(customer, name);
    }

    public void remove(UserId customer) {
        names.remove(customer);
    }

    @Override
    public Optional<String> nameOf(UserId customer) {
        return Optional.ofNullable(names.get(customer));
    }
}
