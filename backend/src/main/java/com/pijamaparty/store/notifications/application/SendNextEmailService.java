package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.application.EmailComposer.Composition;
import com.pijamaparty.store.notifications.domain.EmailType;
import com.pijamaparty.store.notifications.domain.OutboxEmail;
import com.pijamaparty.store.notifications.domain.OutboxEmail.Claim;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
class SendNextEmailService implements SendNextEmail {

    private final ClaimNextEmail claims;
    private final RecordEmailOutcomes outcomes;
    private final Map<EmailType, EmailComposer> composers = new EnumMap<>(EmailType.class);
    private final EmailSender sender;
    private final Clock clock;

    /** @throws IllegalArgumentException unless there is exactly one composer per type of email */
    SendNextEmailService(ClaimNextEmail claims, RecordEmailOutcomes outcomes, List<EmailComposer> composers,
                         EmailSender sender, Clock clock) {
        this.claims = claims;
        this.outcomes = outcomes;
        for (EmailComposer composer : composers) {
            if (this.composers.put(composer.type(), composer) != null) {
                throw new IllegalArgumentException("two composers of " + composer.type());
            }
        }
        for (EmailType type : EmailType.values()) {
            if (!this.composers.containsKey(type)) {
                throw new IllegalArgumentException("no composer of " + type);
            }
        }
        this.sender = sender;
        this.clock = clock;
    }

    /** Claiming and each outcome in its own short transaction; none while composing or sending. */
    @Override
    public Result execute() {
        Optional<Claim> claim = claims.execute();
        if (claim.isEmpty()) {
            return new Result.NoneDue();
        }
        OutboxEmail email = claim.get().email();
        return switch (claim.get()) {
            case Claim.Exhausted exhausted -> new Result.Failed(email.type(), email.reference());
            case Claim.Attempt attempt -> send(email);
        };
    }

    private Result send(OutboxEmail email) {
        return switch (composers.get(email.type()).compose(email.reference())) {
            case Composition.Gone gone -> fail(email);
            case Composition.Composed composed -> switch (sender.send(composed.message())) {
                case EmailSender.Outcome.Sent sent -> {
                    outcomes.sent(email.id(), sent.providerMessageId(), clock.instant());
                    yield new Result.Sent(email.type(), email.reference());
                }
                case EmailSender.Outcome.Rejected rejected -> fail(email);
                case EmailSender.Outcome.Unavailable unavailable -> email.isLastAttempt()
                    ? fail(email)
                    : new Result.Retrying(email.type(), email.reference(), email.attempts());
            };
        };
    }

    private Result fail(OutboxEmail email) {
        outcomes.failed(email.id(), clock.instant());
        return new Result.Failed(email.type(), email.reference());
    }
}
