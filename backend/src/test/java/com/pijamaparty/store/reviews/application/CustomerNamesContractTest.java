package com.pijamaparty.store.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link CustomerNames} (UC-24): the signed-in customer's account name, asked as that customer. */
public abstract class CustomerNamesContractTest<T extends CustomerNames> extends ContractTest<T> {

    /** A customer account with this name. */
    protected abstract UserId givenCustomer(String name);

    @Test
    void givenACustomer_whenAskingItsName_thenItIsTheAccountsName() {
        UserId ana = givenCustomer("Ana María Pérez");
        givenCustomer("Bruno Díaz");

        assertThat(subject.nameOf(ana)).contains("Ana María Pérez");
    }

    @Test
    void givenACustomerWhoseAccountIsGone_whenAskingItsName_thenThereIsNone() {
        assertThat(subject.nameOf(UserId.of(UUID.randomUUID()))).isEmpty();
    }
}
