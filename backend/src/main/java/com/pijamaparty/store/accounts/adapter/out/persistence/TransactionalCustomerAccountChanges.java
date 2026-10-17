package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.accounts.application.CustomerAccountChanges;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * UC-27 (T-E4-15): a change of the signed-in customer's own account, in its own short transaction as the current
 * actor, which the transaction manager passes to Row Level Security (RNF-27).
 */
@Component
class TransactionalCustomerAccountChanges implements CustomerAccountChanges {

    private final TransactionTemplate transactions;

    TransactionalCustomerAccountChanges(PlatformTransactionManager transactionManager) {
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public <R> R changing(Supplier<R> change) {
        return transactions.execute(status -> change.get());
    }
}
