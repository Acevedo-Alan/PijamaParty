package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;

/** What the storefront shows of the signed-in customer (UC-19). Only sent to the customer itself. */
public record CustomerView(UserId id, String email, String name) {

    static CustomerView of(User user) {
        return new CustomerView(user.id(), user.email().value(), user.name());
    }

    @Override
    public String toString() {
        return "CustomerView[id=" + id + ", ***]";
    }
}
