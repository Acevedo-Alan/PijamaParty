package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.accounts.application.ExternalSignIns;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * UC-27 (T-E4-14): the return from a provider happens before anyone is signed in, so it runs in its own transaction
 * as {@code SYSTEM}, which the database lets read users and external accounts, create customers without a password,
 * link external accounts to customers and keep pending links (V34, V35).
 */
@Component
class TransactionalExternalSignIns implements ExternalSignIns {

    private final TransactionTemplate transactions;

    TransactionalExternalSignIns(PlatformTransactionManager transactionManager) {
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public <R> R signingIn(Supplier<R> signIn) {
        return CurrentActor.callAsSystem(() -> transactions.execute(status -> signIn.get()));
    }
}
