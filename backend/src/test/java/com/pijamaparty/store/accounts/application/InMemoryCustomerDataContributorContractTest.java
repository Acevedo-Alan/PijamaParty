package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.api.CustomerDataContributorContractTest;
import com.pijamaparty.store.accounts.api.CustomerDataSection;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.UUID;
import java.util.stream.IntStream;

class InMemoryCustomerDataContributorContractTest
    extends CustomerDataContributorContractTest<InMemoryCustomerDataContributor> {

    @Override
    protected InMemoryCustomerDataContributor createSubject() {
        return new InMemoryCustomerDataContributor(CustomerDataSection.ORDERS);
    }

    @Override
    protected CustomerDataSection expectedSection() {
        return CustomerDataSection.ORDERS;
    }

    @Override
    protected UserId customerWith(int records) {
        UserId customer = UserId.of(UUID.randomUUID());
        subject.arrange(customer, IntStream.range(0, records).mapToObj(n -> "record " + n).toList());
        return customer;
    }
}
