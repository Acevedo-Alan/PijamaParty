package com.pijamaparty.store.notifications.application;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RecordEmailEventService implements RecordEmailEvent {

    private final EmailEvents events;
    private final BounceEmails bounces;
    private final Clock clock;

    RecordEmailEventService(EmailEvents events, BounceEmails bounces, Clock clock) {
        this.events = events;
        this.bounces = bounces;
        this.clock = clock;
    }

    /** The record and the bounce commit together: a failed bounce leaves the notification to be delivered again. */
    @Override
    @Transactional
    public Result execute(Command command) {
        Instant now = clock.instant();
        if (!events.recordFirst(command.provider(), command.eventId(), command.type(), command.providerMessageId(),
            now)) {
            return new Result.AlreadyProcessed();
        }
        if (!command.type().bouncesTheEmail()) {
            return new Result.Recorded(command.type());
        }
        return bounces.bounce(command.providerMessageId(), now)
            .<Result>map(bounced -> new Result.Bounced(bounced.type(), bounced.reference()))
            .orElseGet(Result.UnknownEmail::new);
    }
}
