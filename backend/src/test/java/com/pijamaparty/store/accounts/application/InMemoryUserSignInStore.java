package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;
import java.util.function.Function;

/** Fake of {@link UserSignInStore} over the users of an {@link InMemoryUserStore}. */
public class InMemoryUserSignInStore implements UserSignInStore {

    private final InMemoryUserStore users;

    public InMemoryUserSignInStore(InMemoryUserStore users) {
        this.users = users;
    }

    @Override
    public Optional<User> find(Email email) {
        return users.findByEmail(email);
    }

    /** Synchronized: the real adapter locks the row, so attempts on one account never interleave. */
    @Override
    public synchronized <R> R withUserLockedForSignIn(Email email, Function<Optional<User>, R> record) {
        Optional<User> user = users.findByEmail(email);
        R result = record.apply(user);
        user.ifPresent(users::save);
        return result;
    }

    @Override
    public Optional<User> find(UserId id) {
        return users.findById(id);
    }

    @Override
    public synchronized <R> R withUserLockedForSignIn(UserId id, Function<Optional<User>, R> record) {
        Optional<User> user = users.findById(id);
        R result = record.apply(user);
        user.ifPresent(users::save);
        return result;
    }
}
