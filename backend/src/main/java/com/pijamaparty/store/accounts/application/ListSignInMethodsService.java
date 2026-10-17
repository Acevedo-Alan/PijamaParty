package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read as the customer itself: the database shows a customer only its own external accounts (V34). */
@Service
class ListSignInMethodsService implements ListSignInMethods {

    private final LoadSignInMethods methods;

    ListSignInMethodsService(LoadSignInMethods methods) {
        this.methods = methods;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Actor actor) {
        if (!actor.is(Role.CUSTOMER) || actor.userId().isEmpty()) {
            return new Result.NotSignedIn();
        }
        return methods.findByCustomer(actor.userId().get())
            .<Result>map(found -> new Result.Listed(SignInMethodsView.of(found)))
            .orElseGet(Result.NotSignedIn::new);
    }
}
