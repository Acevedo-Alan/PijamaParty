package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.ordering.application.WithdrawalResolutions.LockedWithdrawal;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-29 (T-E3-26). One transaction: the request stays locked from reading its status until it is resolved. */
@Service
class ResolveWithdrawalService implements ResolveWithdrawal {

    private final WithdrawalResolutions resolutions;
    private final OrderActivity activity;
    private final Clock clock;

    ResolveWithdrawalService(WithdrawalResolutions resolutions, OrderActivity activity, Clock clock) {
        this.resolutions = resolutions;
        this.activity = activity;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        Optional<LockedWithdrawal> found = resolutions.lockForResolution(command.requestId());
        if (found.isEmpty()) {
            return new Result.NotFound();
        }
        LockedWithdrawal withdrawal = found.get();
        if (!withdrawal.status().isResolvable()) {
            return new Result.AlreadyResolved(withdrawal.resolvedAt().orElseThrow());
        }
        // PostgreSQL keeps microseconds: the answer is the time stored, as a later 409 tells it.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        // Never before it was requested (withdrawal_request_resolved_after_ck), should the clocks disagree.
        Instant at = now.isBefore(withdrawal.createdAt()) ? withdrawal.createdAt() : now;
        resolutions.resolve(command.requestId(), at);
        activity.record(new ActivityEntry(command.actor().userId().orElseThrow(), ActivityAction.WITHDRAWAL_RESOLVED,
            ActivitySubject.of(SubjectType.WITHDRAWAL, command.requestId(), withdrawal.code().value())));
        return new Result.Resolved(at);
    }
}
