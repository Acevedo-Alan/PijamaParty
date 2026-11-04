package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.accounts.api.CustomerDirectory;
import com.pijamaparty.store.accounts.api.CustomerDirectory.Customer;
import com.pijamaparty.store.accounts.api.CustomerDirectory.Listing;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * Fake of {@link RegisteredCustomers} with the rules of {@code accounts.api.CustomerDirectory}: 2 to 100 characters
 * of search, store dates in Argentina, newest first, 50 per page.
 */
public class InMemoryRegisteredCustomers implements RegisteredCustomers {

    private static final ZoneId STORE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(2100, 12, 31);

    private final List<Customer> customers = new CopyOnWriteArrayList<>();

    public UUID add(String name, String email, Instant registeredAt) {
        UUID id = UUID.randomUUID();
        customers.add(new Customer(id, name, email, registeredAt));
        return id;
    }

    @Override
    public Listing list(Optional<String> search, Optional<LocalDate> from, Optional<LocalDate> to, int page) {
        String text = search.orElse("").strip();
        int length = text.codePointCount(0, text.length());
        if (length == 1 || length > 100) {
            return new Listing.InvalidSearch();
        }
        boolean outOfRange = Stream.concat(from.stream(), to.stream())
            .anyMatch(day -> day.isBefore(EARLIEST) || day.isAfter(LATEST));
        if (outOfRange || from.isPresent() && to.isPresent() && from.get().isAfter(to.get())) {
            return new Listing.InvalidPeriod();
        }
        List<Customer> rows = customers.stream()
            .filter(customer -> text.isEmpty() || folded(customer.name()).contains(folded(text))
                || folded(customer.email()).contains(folded(text)))
            .filter(customer -> from.map(day -> !customer.registeredAt().isBefore(day.atStartOfDay(STORE).toInstant()))
                .orElse(true))
            .filter(customer -> to.map(day -> customer.registeredAt()
                .isBefore(day.plusDays(1).atStartOfDay(STORE).toInstant())).orElse(true))
            .sorted(Comparator.comparing(Customer::registeredAt).reversed())
            .skip((long) page * CustomerDirectory.PAGE_SIZE)
            .limit(CustomerDirectory.PAGE_SIZE + 1L)
            .toList();
        boolean hasMore = rows.size() > CustomerDirectory.PAGE_SIZE;
        return new Listing.Listed(hasMore ? rows.subList(0, CustomerDirectory.PAGE_SIZE) : rows, hasMore);
    }

    private static String folded(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
