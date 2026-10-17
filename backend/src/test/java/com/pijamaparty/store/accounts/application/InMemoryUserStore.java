package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.InMemoryStore;
import java.util.Optional;

/**
 * Fake of the read/write pair {@link LoadUser} and {@link SaveUser}, of {@link FindUserByEmail} and of
 * {@link AddExternalCustomers}, over the same users.
 */
public class InMemoryUserStore implements LoadUser, SaveUser, FindUserByEmail, AddExternalCustomers {

    private final InMemoryStore<UserId, User> users = InMemoryStore.copying(User::id, User::copy);

    @Override
    public Optional<User> findById(UserId id) {
        return users.findById(id);
    }

    @Override
    public boolean administratorExists() {
        return !users.findAll(user -> user.role() == UserRole.ADMIN).isEmpty();
    }

    @Override
    public Optional<User> findAdministrator() {
        return users.findAll(user -> user.role() == UserRole.ADMIN).stream().findFirst();
    }

    @Override
    public void save(User user) {
        users.save(user);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return users.findAll(user -> user.email().equals(email)).stream().findFirst();
    }

    /** The account and nothing else; {@link InMemorySignInMethods} also drops its links. */
    boolean delete(UserId id) {
        return users.delete(id);
    }

    /** Synchronized: the real adapter lets the email's unique constraint pick one of two inserts. */
    @Override
    public synchronized boolean add(User customer) {
        if (customer.role() != UserRole.CUSTOMER || customer.passwordHash().isPresent()) {
            throw new IllegalArgumentException("a provider creates customers without a password");
        }
        if (findByEmail(customer.email()).isPresent()) {
            return false;
        }
        users.save(customer);
        return true;
    }
}
