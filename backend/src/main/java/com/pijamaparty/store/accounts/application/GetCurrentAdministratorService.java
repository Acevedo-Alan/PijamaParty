package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetCurrentAdministratorService implements GetCurrentAdministrator {

    private final LoadUser users;

    GetCurrentAdministratorService(LoadUser users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Actor actor) {
        if (!actor.is(Role.ADMIN)) {
            return new Result.NotSignedIn();
        }
        return actor.userId()
            .flatMap(users::findById)
            .filter(user -> user.role() == UserRole.ADMIN)
            .<Result>map(user -> new Result.Found(AdministratorView.of(user)))
            .orElseGet(Result.NotSignedIn::new);
    }
}
