package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.accounts.api.CustomerDirectory.Customer;
import com.pijamaparty.store.accounts.api.CustomerDirectory.Listing;
import com.pijamaparty.store.platform.domain.Role;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-34 (T-E8-10): the customers from {@code accounts} with their order counts from {@code ordering}, composed here
 * because {@code ordering} depends on {@code accounts} (ARCHITECTURE.md section 3, rule 3). One read transaction.
 */
@Service
class ListCustomersService implements ListCustomers {

    private final RegisteredCustomers customers;
    private final CustomerOrderTotals orderTotals;

    ListCustomersService(RegisteredCustomers customers, CustomerOrderTotals orderTotals) {
        this.customers = customers;
        this.orderTotals = orderTotals;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        return switch (customers.list(query.search(), query.from(), query.to(), query.page())) {
            case Listing.InvalidSearch invalid -> new Result.InvalidSearch();
            case Listing.InvalidPeriod invalid -> new Result.InvalidPeriod();
            case Listing.Listed listed -> {
                Map<UUID, Long> orders = orderTotals.countsOf(listed.customers().stream().map(Customer::id).toList());
                yield new Result.Listed(listed.customers().stream()
                    .map(customer -> new CustomerRow(customer.id(), customer.name(), customer.email(),
                        customer.registeredAt(), orders.getOrDefault(customer.id(), 0L)))
                    .toList(), listed.hasMore());
            }
        };
    }
}
