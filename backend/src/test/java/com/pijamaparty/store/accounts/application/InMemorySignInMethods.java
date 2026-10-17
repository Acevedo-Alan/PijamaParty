package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.SignInMethods;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;

/**
 * Fake of {@link LoadSignInMethods} and {@link UnlinkExternalIdentities} over the users of an {@link InMemoryUserStore}
 * and the links of {@link InMemoryExternalIdentities}. The lock is not modelled: the use-case tests run one at a time.
 */
public class InMemorySignInMethods implements LoadSignInMethods, UnlinkExternalIdentities {

    private final InMemoryUserStore users;
    private final InMemoryExternalIdentities identities;

    public InMemorySignInMethods(InMemoryUserStore users, InMemoryExternalIdentities identities) {
        this.users = users;
        this.identities = identities;
    }

    @Override
    public Optional<SignInMethods> findByCustomer(UserId customer) {
        return users.findById(customer).map(user -> new SignInMethods(customer, user.passwordHash().isPresent(),
            identities.all().stream().filter(identity -> identity.userId().equals(customer)).toList()));
    }

    @Override
    public Optional<SignInMethods> lockByCustomer(UserId customer) {
        return findByCustomer(customer);
    }

    @Override
    public void unlink(ExternalIdentity identity) {
        identities.all().stream()
            .filter(each -> each.id().equals(identity.id()) && each.userId().equals(identity.userId()))
            .forEach(each -> identities.unlink(each.id()));
    }
}
