package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read as the customer itself: the database shows a customer only its own account (V29). */
@Service
class GetCurrentCustomerService implements GetCurrentCustomer {

    private final LoadUser users;

    GetCurrentCustomerService(LoadUser users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Actor actor) {
        if (!actor.is(Role.CUSTOMER)) {
            return new Result.NotSignedIn();
        }
        return actor.userId()
            .flatMap(users::findById)
            .filter(user -> user.role() == UserRole.CUSTOMER)
            .<Result>map(user -> new Result.Found(CustomerView.of(user)))
            .orElseGet(Result.NotSignedIn::new);
    }
}
