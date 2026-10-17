package com.pijamaparty.store.accounts.domain;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-27 (T-E4-15): how a customer signs in: its password, if it has one, and its linked external accounts, at most one
 * of each provider (V34). The last way in cannot be removed: a password or another provider must remain. A password
 * counts even while the email is unverified, because its sign-in sends a new verification email (UC-19).
 */
public record SignInMethods(UserId customer, boolean hasPassword, List<ExternalIdentity> identities) {

    public SignInMethods {
        Objects.requireNonNull(customer, "customer");
        Objects.requireNonNull(identities, "identities");
        if (identities.stream().anyMatch(identity -> !identity.userId().equals(customer))) {
            throw new IllegalArgumentException("every identity belongs to the customer");
        }
        if (identities.stream().map(identity -> identity.account().provider()).distinct().count()
            != identities.size()) {
            throw new IllegalArgumentException("at most one external account of each provider");
        }
        // In the order of the providers: Google first.
        identities = identities.stream()
            .sorted(Comparator.comparing(identity -> identity.account().provider()))
            .toList();
    }

    public Optional<ExternalIdentity> identity(ExternalProvider provider) {
        return identities.stream().filter(identity -> identity.account().provider() == provider).findFirst();
    }

    /** Decides whether {@code provider} can be unlinked, leaving the customer a way in; the adapter deletes the row. */
    public UnlinkOutcome unlink(ExternalProvider provider) {
        Optional<ExternalIdentity> identity = identity(provider);
        if (identity.isEmpty()) {
            return new UnlinkOutcome.NotLinked();
        }
        if (!hasPassword && identities.size() == 1) {
            return new UnlinkOutcome.LastSignInMethod();
        }
        return new UnlinkOutcome.Unlinked(identity.get());
    }

    public sealed interface UnlinkOutcome {

        record Unlinked(ExternalIdentity identity) implements UnlinkOutcome {}

        /** The customer has no account of that provider. */
        record NotLinked() implements UnlinkOutcome {}

        /** No password and no other provider: the customer would be left without a way in. */
        record LastSignInMethod() implements UnlinkOutcome {}
    }
}
