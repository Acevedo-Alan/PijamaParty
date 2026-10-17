package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Fake of {@link RememberCheckoutDetails}: the phone and the address book of each known customer, up to 10. */
public class InMemoryRememberCheckoutDetails implements RememberCheckoutDetails {

    private static final int MAX_ADDRESSES = 10;

    private final Map<UserId, String> phones = new HashMap<>();
    private final Map<UserId, List<AddressToSave>> books = new HashMap<>();
    private int calls;

    public void customer(UserId customer) {
        books.putIfAbsent(customer, new ArrayList<>());
    }

    public void address(UserId customer, AddressToSave address) {
        books.get(customer).add(address);
    }

    @Override
    public void remember(UserId customer, String phone, Optional<AddressToSave> address) {
        calls++;
        List<AddressToSave> book = books.get(customer);
        if (book == null) {
            return;
        }
        phones.put(customer, phone);
        address.filter(each -> book.size() < MAX_ADDRESSES).ifPresent(book::add);
    }

    public Optional<String> phoneOf(UserId customer) {
        return Optional.ofNullable(phones.get(customer));
    }

    public List<AddressToSave> addressesOf(UserId customer) {
        return List.copyOf(books.getOrDefault(customer, List.of()));
    }

    /** How many checkouts were remembered, of known customers or not. */
    public int calls() {
        return calls;
    }
}
