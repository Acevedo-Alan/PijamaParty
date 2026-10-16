package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.User;

class InMemoryUserTokensContractTest extends UserTokensContractTest<InMemoryUserTokens> {

    @Override
    protected InMemoryUserTokens createSubject() {
        return new InMemoryUserTokens();
    }

    /** The fake keeps no users: a token only names its user. */
    @Override
    protected void arrange(User user) {
    }
}
