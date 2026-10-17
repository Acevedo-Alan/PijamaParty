package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Objects;
import java.util.Optional;

/**
 * What the storefront shows of the signed-in customer (UC-19), and prefills at the checkout (UC-21). Only sent to the
 * customer itself.
 *
 * @param phone left by its last purchase (T-E4-06); empty until then
 */
public record CustomerView(UserId id, String email, String name, Optional<String> phone) {

    public CustomerView {
        Objects.requireNonNull(phone, "phone");
    }

    static CustomerView of(User user) {
        return new CustomerView(user.id(), user.email().value(), user.name(), user.phone());
    }

    @Override
    public String toString() {
        return "CustomerView[id=" + id + ", ***]";
    }
}
