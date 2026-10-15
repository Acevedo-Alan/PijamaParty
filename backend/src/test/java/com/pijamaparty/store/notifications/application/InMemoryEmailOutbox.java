package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.EmailStatus;
import com.pijamaparty.store.notifications.domain.EmailType;
import com.pijamaparty.store.notifications.domain.OutboxEmail;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Fake of {@link EnqueueEmails}, {@link LockDueEmails}, {@link RecordEmailOutcomes}, {@link EmailStatuses},
 * {@link BounceEmails} and {@link ResendEmails} over the same outbox. Locks are not modeled.
 */
public class InMemoryEmailOutbox
    implements EnqueueEmails, LockDueEmails, RecordEmailOutcomes, EmailStatuses, BounceEmails, ResendEmails {

    /** An email of the outbox as the table keeps it. */
    public record Stored(UUID id, EmailType type, UUID reference, int version, String status, int attempts,
                         Instant nextAttemptAt, Optional<String> providerMessageId, Optional<Instant> sentAt) {

        Stored with(String newStatus, int newAttempts, Instant newNextAttemptAt, Optional<String> messageId,
                    Optional<Instant> newSentAt) {
            return new Stored(id, type, reference, version, newStatus, newAttempts, newNextAttemptAt, messageId,
                newSentAt);
        }
    }

    private static final Comparator<Stored> OLDEST_DUE_FIRST = Comparator.comparing(Stored::nextAttemptAt)
        .thenComparing(stored -> stored.id().toString());

    private final Map<UUID, Stored> emails = new LinkedHashMap<>();

    /** Test inspection: the first version of the email; empty when the outbox has no such email. */
    public synchronized Optional<Stored> find(EmailType type, UUID reference) {
        return versions(type, reference).stream().findFirst();
    }

    /** Test inspection: every version of the email, from the first. */
    public synchronized List<Stored> versions(EmailType type, UUID reference) {
        return emails.values().stream()
            .filter(stored -> stored.type() == type && stored.reference().equals(reference))
            .sorted(Comparator.comparingInt(Stored::version))
            .toList();
    }

    /** Test inspection: how many emails the outbox holds. */
    public synchronized int size() {
        return emails.size();
    }

    @Override
    public synchronized boolean enqueue(EmailType type, UUID reference, Instant at) {
        if (find(type, reference).isPresent()) {
            return false;
        }
        UUID id = UUID.randomUUID();
        emails.put(id, new Stored(id, type, reference, 0, "PENDING", 0, at, Optional.empty(), Optional.empty()));
        return true;
    }

    @Override
    public synchronized boolean lockUnattempted(EmailType type, UUID reference) {
        return versions(type, reference).stream()
            .anyMatch(stored -> stored.status().equals("PENDING") && stored.attempts() == 0);
    }

    @Override
    public synchronized int enqueueNextVersion(EmailType type, UUID reference, Instant at) {
        List<Stored> versions = versions(type, reference);
        int version = versions.isEmpty() ? 1 : versions.getLast().version() + 1;
        UUID id = UUID.randomUUID();
        emails.put(id, new Stored(id, type, reference, version, "PENDING", 0, at, Optional.empty(),
            Optional.empty()));
        return version;
    }

    @Override
    public synchronized Optional<OutboxEmail> lockNextDue(Instant now) {
        return emails.values().stream()
            .filter(stored -> stored.status().equals("PENDING") && !stored.nextAttemptAt().isAfter(now))
            .min(OLDEST_DUE_FIRST)
            .map(stored -> new OutboxEmail(stored.id(), stored.type(), stored.reference(), stored.attempts()));
    }

    @Override
    public synchronized Optional<EmailStatus> statusOf(EmailType type, UUID reference) {
        return versions(type, reference).stream().reduce((first, second) -> second)
            .map(stored -> EmailStatus.valueOf(stored.status()));
    }

    @Override
    public synchronized void attempting(UUID email, int attempts, Instant retryAt, Instant at) {
        Stored stored = emails.get(email);
        if (stored != null && stored.status().equals("PENDING")) {
            emails.put(email, stored.with("PENDING", attempts, retryAt, Optional.empty(), Optional.empty()));
        }
    }

    @Override
    public synchronized void sent(UUID email, String providerMessageId, Instant at) {
        Stored stored = emails.get(email);
        if (stored != null && stored.status().equals("PENDING")) {
            emails.put(email, stored.with("SENT", stored.attempts(), stored.nextAttemptAt(),
                Optional.of(providerMessageId), Optional.of(at)));
        }
    }

    @Override
    public synchronized Optional<Bounced> bounce(String providerMessageId, Instant at) {
        Optional<Stored> sent = emails.values().stream()
            .filter(stored -> stored.status().equals("SENT")
                && stored.providerMessageId().filter(providerMessageId::equals).isPresent())
            .findFirst();
        sent.ifPresent(stored -> emails.put(stored.id(), stored.with("BOUNCED", stored.attempts(),
            stored.nextAttemptAt(), stored.providerMessageId(), stored.sentAt())));
        return sent.map(stored -> new Bounced(stored.type(), stored.reference()));
    }

    @Override
    public synchronized void failed(UUID email, Instant at) {
        Stored stored = emails.get(email);
        if (stored != null && stored.status().equals("PENDING")) {
            emails.put(email, stored.with("FAILED", stored.attempts(), stored.nextAttemptAt(), Optional.empty(),
                Optional.empty()));
        }
    }
}
