package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.platform.domain.UserId;

/**
 * UC-37: the activity entries of the administrator's sign-ins, about the administrator, without a label and without
 * fields: never a code, a secret or an email (T-E8-03).
 */
final class AdministratorEntries {

    private AdministratorEntries() {
    }

    static ActivityEntry entry(UserId administrator, ActivityAction action) {
        return new ActivityEntry(administrator, action,
            ActivitySubject.of(SubjectType.ADMINISTRATOR, administrator.value()));
    }
}
