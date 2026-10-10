package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.InMemoryStore;
import java.util.Optional;

/** Fake of the read/write pair {@link LoadUser} and {@link SaveUser}. */
public class InMemoryUserStore implements LoadUser, SaveUser {

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
    public void save(User user) {
        users.save(user);
    }

    /** For {@link InMemoryUserSignInStore}, which shares these users. */
    Optional<User> findByEmail(Email email) {
        return users.findAll(user -> user.email().equals(email)).stream().findFirst();
    }
}
