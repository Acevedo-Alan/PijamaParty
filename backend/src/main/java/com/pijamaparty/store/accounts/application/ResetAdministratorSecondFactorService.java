package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.User;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Runs at startup as {@code SYSTEM} (the caller binds it), like the creation of the first administrator. */
@Service
class ResetAdministratorSecondFactorService implements ResetAdministratorSecondFactor {

    private final LoadUser loadUser;
    private final SaveUser saveUser;
    private final RecoveryCodes recoveryCodes;
    private final Clock clock;

    ResetAdministratorSecondFactorService(LoadUser loadUser, SaveUser saveUser, RecoveryCodes recoveryCodes,
                                          Clock clock) {
        this.loadUser = loadUser;
        this.saveUser = saveUser;
        this.recoveryCodes = recoveryCodes;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute() {
        Optional<User> administrator = loadUser.findAdministrator();
        if (administrator.isEmpty() || administrator.get().secondFactor().isEmpty()) {
            return new Result.NothingToReset();
        }
        User user = administrator.get();
        user.resetSecondFactor(clock.instant());
        saveUser.save(user);
        recoveryCodes.deleteAll(user.id());
        return new Result.Reset();
    }
}
