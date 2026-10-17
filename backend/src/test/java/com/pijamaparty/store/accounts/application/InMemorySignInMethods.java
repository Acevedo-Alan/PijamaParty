package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.SignInMethods;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;

/**
 * Fake of {@link LoadSignInMethods}, {@link UnlinkExternalIdentities} and {@link DeleteCustomers} over the users of an
 * {@link InMemoryUserStore} and the links of {@link InMemoryExternalIdentities}; deleting a customer drops its links,
 * as the foreign keys do. The lock is not modelled: the use-case tests run one at a time.
 */
public class InMemorySignInMethods implements LoadSignInMethods, UnlinkExternalIdentities, DeleteCustomers {

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

    @Override
    public boolean delete(UserId customer) {
        if (users.findById(customer).filter(user -> user.role() == UserRole.CUSTOMER).isEmpty()) {
            return false;
        }
        identities.all().stream()
            .filter(each -> each.userId().equals(customer))
            .forEach(each -> identities.unlink(each.id()));
        return users.delete(customer);
    }
}
