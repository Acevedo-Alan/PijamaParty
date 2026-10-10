package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;

class InMemoryUserSignInStoreContractTest extends UserSignInStoreContractTest<InMemoryUserSignInStore> {

    private InMemoryUserStore users;

    @Override
    protected InMemoryUserSignInStore createSubject() {
        users = new InMemoryUserStore();
        return new InMemoryUserSignInStore(users);
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
