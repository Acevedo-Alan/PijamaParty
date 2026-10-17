package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.accounts.application.PasswordRecoveries;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * UC-19 (T-E4-03): a recovery request happens before anyone is signed in, so it runs in its own transaction as
 * {@code SYSTEM}, which reads the user without changing it; the reset email is enqueued in that transaction.
 */
@Repository
class JpaPasswordRecoveries implements PasswordRecoveries {

    private final UserJpaRepository users;
    private final TransactionTemplate transactions;

    JpaPasswordRecoveries(UserJpaRepository users, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void requesting(Email email, Consumer<Optional<User>> request) {
        CurrentActor.runAsSystem(() -> transactions.executeWithoutResult(status ->
            request.accept(users.findOneByEmail(email.value()).map(UserEntity::toDomain))));
    }
}
