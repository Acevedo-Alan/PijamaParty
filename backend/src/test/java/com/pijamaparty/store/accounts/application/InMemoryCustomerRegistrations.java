package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;

/** Fake of {@link CustomerRegistrations} over the users of an {@link InMemoryUserStore}. */
public class InMemoryCustomerRegistrations implements CustomerRegistrations {

    private final InMemoryUserStore users;

    public InMemoryCustomerRegistrations(InMemoryUserStore users) {
        this.users = users;
    }

    /** Synchronized: the real adapter lets the email's unique constraint pick one of two registrations. */
    @Override
    public synchronized boolean register(User customer, Runnable whenRegistered) {
        if (customer.role() != UserRole.CUSTOMER || customer.emailVerified()) {
            throw new IllegalArgumentException("only unverified customers register");
        }
        if (users.findByEmail(customer.email()).isPresent()) {
            return false;
        }
        users.save(customer);
        whenRegistered.run();
        return true;
    }
}
