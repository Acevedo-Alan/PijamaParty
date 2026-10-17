package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.SignInMethods;
import com.pijamaparty.store.platform.domain.Role;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * As the customer itself (V34 lets it unlink its own). The account row is locked first, so two concurrent unlinks,
 * or an unlink and a new password, run one after the other and each sees what the other left.
 */
@Service
class UnlinkExternalAccountService implements UnlinkExternalAccount {

    private final LoadSignInMethods methods;
    private final UnlinkExternalIdentities identities;

    UnlinkExternalAccountService(LoadSignInMethods methods, UnlinkExternalIdentities identities) {
        this.methods = methods;
        this.identities = identities;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.CUSTOMER) || command.actor().userId().isEmpty()) {
            return new Result.NotSignedIn();
        }
        Optional<SignInMethods> found = methods.lockByCustomer(command.actor().userId().get());
        if (found.isEmpty()) {
            return new Result.NotSignedIn();
        }
        return switch (found.get().unlink(command.provider())) {
            case SignInMethods.UnlinkOutcome.Unlinked unlinked -> {
                identities.unlink(unlinked.identity());
                yield new Result.Unlinked();
            }
            case SignInMethods.UnlinkOutcome.NotLinked notLinked -> new Result.NotLinked();
            case SignInMethods.UnlinkOutcome.LastSignInMethod last -> new Result.LastSignInMethod();
        };
    }
}
