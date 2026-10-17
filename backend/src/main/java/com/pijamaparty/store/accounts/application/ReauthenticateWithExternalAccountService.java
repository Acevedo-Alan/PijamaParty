package com.pijamaparty.store.accounts.application;

import org.springframework.stereotype.Service;

/**
 * Not {@code @Transactional}: {@link ExternalSignIns} runs it in one short transaction as {@code SYSTEM}, which reads
 * the external accounts of every customer (V34), as the provider's other returns do.
 */
@Service
class ReauthenticateWithExternalAccountService implements ReauthenticateWithExternalAccount {

    private final ExternalSignIns signIns;
    private final FindExternalIdentities identities;

    ReauthenticateWithExternalAccountService(ExternalSignIns signIns, FindExternalIdentities identities) {
        this.signIns = signIns;
        this.identities = identities;
    }

    @Override
    public Result execute(Command command) {
        boolean linked = signIns.signingIn(() -> identities.find(command.account())
            .filter(identity -> identity.userId().equals(command.customer()))
            .isPresent());
        return linked ? new Result.Reauthenticated() : new Result.NotLinked();
    }
}
