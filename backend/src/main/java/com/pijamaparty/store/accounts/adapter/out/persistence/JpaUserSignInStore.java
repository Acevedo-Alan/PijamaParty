package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.accounts.application.UserSignInStore;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sign-in happens before anyone is signed in: the attempt runs in its own transaction as {@code SYSTEM} (RNF-27),
 * which may only read users and change the attempt counter and the lock (V5 policies and trigger).
 */
@Repository
class JpaUserSignInStore implements UserSignInStore {

    private final UserJpaRepository users;
    private final TransactionTemplate transactions;
    private final TransactionTemplate readOnly;

    JpaUserSignInStore(UserJpaRepository users, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.transactions = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    @Override
    public Optional<User> find(Email email) {
        return CurrentActor.callAsSystem(() -> readOnly.execute(status ->
            users.findOneByEmail(email.value()).map(UserEntity::toDomain)));
    }

    @Override
    public <R> R withUserLockedForSignIn(Email email, Function<Optional<User>, R> record) {
        return CurrentActor.callAsSystem(() -> transactions.execute(status -> {
            Optional<UserEntity> row = users.findByEmail(email.value());
            Optional<User> user = row.map(UserEntity::toDomain);
            R result = record.apply(user);
            // The entity is managed: the changed counter and lock are flushed at commit.
            row.ifPresent(entity -> entity.copySignInStateFrom(user.orElseThrow()));
            return result;
        }));
    }
}
