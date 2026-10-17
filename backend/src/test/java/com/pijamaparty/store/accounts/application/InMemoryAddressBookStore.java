package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.Address;
import com.pijamaparty.store.accounts.domain.AddressBook;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fake of the read/write pair {@link LoadAddressBook} and {@link SaveAddressBook}, over the accounts of an
 * {@link InMemoryUserStore}. Addresses are immutable, so it keeps them and rebuilds a new book on every read.
 */
public class InMemoryAddressBookStore implements LoadAddressBook, SaveAddressBook {

    private final InMemoryUserStore users;
    private final Map<UserId, List<Address>> books = new ConcurrentHashMap<>();

    public InMemoryAddressBookStore(InMemoryUserStore users) {
        this.users = users;
    }

    @Override
    public AddressBook findByCustomer(UserId customer) {
        return AddressBook.of(customer, books.getOrDefault(customer, List.of()));
    }

    @Override
    public Optional<AddressBook> lockByCustomer(UserId customer) {
        return users.findById(customer).map(user -> findByCustomer(customer));
    }

    @Override
    public void save(AddressBook book) {
        books.put(book.owner(), List.copyOf(book.addresses()));
    }
}
