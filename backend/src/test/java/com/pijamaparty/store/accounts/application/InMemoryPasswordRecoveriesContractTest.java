package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.User;

class InMemoryPasswordRecoveriesContractTest extends PasswordRecoveriesContractTest<InMemoryPasswordRecoveries> {

    private final InMemoryUserStore users = new InMemoryUserStore();

    @Override
    protected InMemoryPasswordRecoveries createSubject() {
        return new InMemoryPasswordRecoveries(users);
    }

    @Override
    protected void arrange(User user) {
        users.save(user);
    }
}
