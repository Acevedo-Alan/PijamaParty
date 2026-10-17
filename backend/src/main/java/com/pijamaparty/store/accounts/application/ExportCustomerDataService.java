package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.api.CustomerDataContributor;
import com.pijamaparty.store.accounts.api.CustomerDataSection;
import com.pijamaparty.store.accounts.domain.SignInMethods;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One read-only transaction as the customer itself, which the contributors of other modules join: Row Level Security
 * shows it only its own account, external accounts, addresses and orders (RNF-27).
 */
@Service
class ExportCustomerDataService implements ExportCustomerData {

    private final LoadUser users;
    private final LoadSignInMethods methods;
    private final LoadAddressBook books;
    private final List<CustomerDataContributor> contributors;
    private final Clock clock;

    ExportCustomerDataService(LoadUser users, LoadSignInMethods methods, LoadAddressBook books,
                              List<CustomerDataContributor> contributors, Clock clock) {
        if (contributors.stream().map(CustomerDataContributor::section).distinct().count() != contributors.size()) {
            throw new IllegalArgumentException("at most one contributor per section of the customer's data");
        }
        this.users = users;
        this.methods = methods;
        this.books = books;
        this.contributors = List.copyOf(contributors);
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Actor actor) {
        if (!actor.is(Role.CUSTOMER) || actor.userId().isEmpty()) {
            return new Result.NotSignedIn();
        }
        UserId customer = actor.userId().get();
        Optional<User> user = users.findById(customer).filter(found -> found.role() == UserRole.CUSTOMER);
        Optional<SignInMethods> signInMethods = methods.findByCustomer(customer);
        if (user.isEmpty() || signInMethods.isEmpty()) {
            return new Result.NotSignedIn();
        }
        Map<CustomerDataSection, List<?>> sections = new EnumMap<>(CustomerDataSection.class);
        contributors.forEach(contributor -> sections.put(contributor.section(), contributor.export(customer)));
        User found = user.get();
        var profile = new CustomerDataExport.Profile(found.email().value(), found.name(), found.phone(),
            found.emailVerified(), found.createdAt());
        return new Result.Exported(new CustomerDataExport(clock.instant(), profile,
            SignInMethodsView.of(signInMethods.get()),
            books.findByCustomer(customer).addresses().stream().map(AddressView::of).toList(), sections));
    }
}
