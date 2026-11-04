package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.api.CustomerDirectory.Customer;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fake of {@link CustomerPages}: the users the test gives, with the database's order and folding. */
public class InMemoryCustomerPages implements CustomerPages {

    private record User(Customer customer, String role) {}

    private final List<User> users = new CopyOnWriteArrayList<>();

    public void add(UUID id, String name, String email, String role, Instant registeredAt) {
        users.add(new User(new Customer(id, name, email, registeredAt), role));
    }

    @Override
    public List<Customer> page(CustomerFilter filter, int offset, int limit) {
        return users.stream()
            .filter(user -> user.role().equals("CUSTOMER"))
            .map(User::customer)
            .filter(customer -> filter.search().map(search -> folded(customer.name()).contains(folded(search.text()))
                || folded(customer.email()).contains(folded(search.text()))).orElse(true))
            .filter(customer -> filter.registeredFrom().map(from -> !customer.registeredAt().isBefore(from))
                .orElse(true))
            .filter(customer -> filter.registeredBefore().map(customer.registeredAt()::isBefore).orElse(true))
            .sorted(Comparator.comparing(Customer::registeredAt).thenComparing(customer -> customer.id().toString())
                .reversed())
            .skip(offset)
            .limit(limit)
            .toList();
    }

    /** As {@code lower(unaccent(...))}. */
    private static String folded(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
