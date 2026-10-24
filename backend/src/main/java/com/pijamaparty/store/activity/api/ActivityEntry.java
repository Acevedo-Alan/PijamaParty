package com.pijamaparty.store.activity.api;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.List;
import java.util.Objects;

/**
 * UC-37: one change made from the back office, as its use case records it.
 *
 * @param administrator who made it: the signed-in administrator, or the one signing in for the ACCOUNT entries
 * @param changes the fields that changed, at most 20; empty when the action says it all (published, deleted...)
 */
public record ActivityEntry(UserId administrator, ActivityAction action, ActivitySubject subject,
                            List<FieldChange> changes) {

    public ActivityEntry {
        Objects.requireNonNull(administrator, "administrator");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(subject, "subject");
        changes = List.copyOf(changes);
    }

    public ActivityEntry(UserId administrator, ActivityAction action, ActivitySubject subject) {
        this(administrator, action, subject, List.of());
    }
}
