package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;

/** What the back office shows of the signed-in administrator. Only sent to the administrator itself. */
public record AdministratorView(UserId id, String email, String name) {

    static AdministratorView of(User user) {
        return new AdministratorView(user.id(), user.email().value(), user.name());
    }
}
