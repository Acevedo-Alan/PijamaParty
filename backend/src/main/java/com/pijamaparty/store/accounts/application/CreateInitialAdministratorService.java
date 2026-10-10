package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.PasswordRules;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.platform.application.IdGenerator;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Runs at startup as {@code SYSTEM} (the caller binds it): RLS lets only the system create the administrator. */
@Service
class CreateInitialAdministratorService implements CreateInitialAdministrator {

    private final LoadUser loadUser;
    private final SaveUser saveUser;
    private final PasswordHasher passwords;
    private final IdGenerator ids;
    private final Clock clock;

    CreateInitialAdministratorService(LoadUser loadUser, SaveUser saveUser, PasswordHasher passwords, IdGenerator ids,
                                      Clock clock) {
        this.loadUser = loadUser;
        this.saveUser = saveUser;
        this.passwords = passwords;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (loadUser.administratorExists()) {
            return new Result.AlreadyExists();
        }
        Optional<Email> email = Email.parse(command.email());
        if (email.isEmpty()) {
            return new Result.InvalidEmail();
        }
        String name = command.name() == null ? null : command.name().strip();
        if (!User.isValidName(name)) {
            return new Result.InvalidName();
        }
        Optional<PasswordRules.Violation> violation = PasswordRules.check(command.password());
        if (violation.isPresent()) {
            return new Result.InvalidPassword(violation.get());
        }
        User administrator = User.newAdministrator(UserId.of(ids.newId()), email.get(), name,
            passwords.hash(command.password()), clock.instant());
        saveUser.save(administrator);
        return new Result.Created(administrator.id());
    }
}
