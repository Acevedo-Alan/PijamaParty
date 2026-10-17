package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.api.CustomerDataContributor;
import com.pijamaparty.store.accounts.api.CustomerDataSection;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Fake of {@link CustomerDataContributor}: the records of one section, by customer. */
public class InMemoryCustomerDataContributor implements CustomerDataContributor {

    private final CustomerDataSection section;
    private final Map<UserId, List<String>> records = new ConcurrentHashMap<>();

    public InMemoryCustomerDataContributor(CustomerDataSection section) {
        this.section = section;
    }

    /** Test arrangement: the customer's records, replacing any it had. */
    public void arrange(UserId customer, List<String> customerRecords) {
        records.put(customer, List.copyOf(customerRecords));
    }

    @Override
    public CustomerDataSection section() {
        return section;
    }

    @Override
    public List<String> export(UserId customer) {
        return records.getOrDefault(customer, List.of());
    }
}
