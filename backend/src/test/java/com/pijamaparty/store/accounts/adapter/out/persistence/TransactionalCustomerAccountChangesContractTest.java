package com.pijamaparty.store.accounts.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.application.CustomerAccountChangesContractTest;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** The adapter opens its own transaction as the current actor: the signed-in customer stays who it is. */
@ApiTest
class TransactionalCustomerAccountChangesContractTest
    extends CustomerAccountChangesContractTest<TransactionalCustomerAccountChanges> {

    private final TransactionalCustomerAccountChanges adapter;

    TransactionalCustomerAccountChangesContractTest(TransactionalCustomerAccountChanges adapter) {
        this.adapter = adapter;
    }

    @Override
    protected TransactionalCustomerAccountChanges createSubject() {
        return adapter;
    }

    @Test
    void givenACustomer_whenChanging_thenTheBlockRunsAsThatCustomerInATransaction() {
        var customer = Actor.customer(UserId.of(UUID.randomUUID()), Optional.empty(), Optional.empty());

        Actor actor = CurrentActor.callAs(customer, () -> subject.changing(() -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return CurrentActor.get().orElseThrow();
        }));

        assertThat(actor).isEqualTo(customer);
    }
}
