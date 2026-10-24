package com.pijamaparty.store.activity.application;

import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivityLog;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.domain.ActivityLimits;
import com.pijamaparty.store.platform.application.IdGenerator;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** UC-37: stores the entry of a change within the limits of {@link ActivityLimits}, in the change's transaction. */
@Service
class ActivityLogService implements ActivityLog {

    private final SaveActivity saveActivity;
    private final IdGenerator ids;
    private final Clock clock;

    ActivityLogService(SaveActivity saveActivity, IdGenerator ids, Clock clock) {
        this.saveActivity = saveActivity;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(ActivityEntry entry) {
        ActivityLimits.changes(entry.changes().size());
        ActivitySubject subject = entry.subject();
        ActivityEntry limited = new ActivityEntry(entry.administrator(), entry.action(),
            new ActivitySubject(subject.type(), subject.id(), subject.label().map(ActivityLimits::text)),
            entry.changes().stream()
                .map(change -> new FieldChange(ActivityLimits.field(change.field()),
                    change.before().map(ActivityLimits::text), change.after().map(ActivityLimits::text)))
                .toList());
        saveActivity.save(new RecordedActivity(ids.newId(), clock.instant(), limited));
    }
}
