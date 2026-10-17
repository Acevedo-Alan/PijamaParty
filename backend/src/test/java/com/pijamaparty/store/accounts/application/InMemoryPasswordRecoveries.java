package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import java.util.Optional;
import java.util.function.Consumer;

/** Fake of {@link PasswordRecoveries} over the users of an {@link InMemoryUserStore}. */
public class InMemoryPasswordRecoveries implements PasswordRecoveries {

    private final InMemoryUserStore users;

    public InMemoryPasswordRecoveries(InMemoryUserStore users) {
        this.users = users;
    }

    @Override
    public void requesting(Email email, Consumer<Optional<User>> request) {
        request.accept(users.findByEmail(email));
    }
}
