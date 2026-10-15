package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.Actor;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * UC-29 (T-E3-26): the administrator marks a withdrawal request as resolved, once it arranged the return and the
 * refund. Any received request may be resolved; the order is not touched and no email is sent.
 */
public interface ResolveWithdrawal {

    Result execute(Command command);

    record Command(Actor actor, UUID requestId) {

        public Command {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(requestId, "requestId");
        }
    }

    sealed interface Result {

        record Resolved(Instant resolvedAt) implements Result {}

        record AlreadyResolved(Instant resolvedAt) implements Result {}

        record NotFound() implements Result {}

        record Forbidden() implements Result {}
    }
}
