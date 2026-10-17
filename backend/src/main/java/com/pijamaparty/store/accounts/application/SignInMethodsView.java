package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.accounts.domain.SignInMethods;
import java.time.Instant;
import java.util.List;

/**
 * UC-27 (T-E4-15): how the customer signs in, for "Mi cuenta": whether it has a password and its linked external
 * accounts, Google first. The email is the one each was linked with; personal data, the owner's own.
 */
public record SignInMethodsView(boolean hasPassword, List<Identity> identities) {

    public SignInMethodsView {
        identities = List.copyOf(identities);
    }

    static SignInMethodsView of(SignInMethods methods) {
        return new SignInMethodsView(methods.hasPassword(), methods.identities().stream()
            .map(identity -> new Identity(identity.account().provider(), identity.email().value(),
                identity.createdAt()))
            .toList());
    }

    public record Identity(ExternalProvider provider, String email, Instant linkedAt) {

        @Override
        public String toString() {
            return "Identity[provider=" + provider + ", ***]";
        }
    }
}
