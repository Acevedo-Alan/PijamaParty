package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.api.CustomerCounts.Counts;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fake of {@link CountCustomers}: the users the test gives, each with its role. */
public class InMemoryCountCustomers implements CountCustomers {

    private record User(String role, Instant registeredAt) {}

    private final List<User> users = new CopyOnWriteArrayList<>();

    public void add(String role, Instant registeredAt) {
        users.add(new User(role, registeredAt));
    }

    @Override
    public Counts count(Instant since) {
        List<User> customers = users.stream().filter(user -> user.role().equals("CUSTOMER")).toList();
        return new Counts(customers.size(),
            customers.stream().filter(user -> !user.registeredAt().isBefore(since)).count());
    }
}
