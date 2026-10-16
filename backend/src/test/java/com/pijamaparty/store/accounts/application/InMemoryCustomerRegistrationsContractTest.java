package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;

class InMemoryCustomerRegistrationsContractTest extends CustomerRegistrationsContractTest<InMemoryCustomerRegistrations> {

    private final InMemoryUserStore users = new InMemoryUserStore();

    @Override
    protected InMemoryCustomerRegistrations createSubject() {
        return new InMemoryCustomerRegistrations(users);
    }

    @Override
    protected void arrange(User user) {
        users.save(user);
    }

    @Override
    protected Optional<User> stored(UserId id) {
        return users.findById(id);
    }
}
