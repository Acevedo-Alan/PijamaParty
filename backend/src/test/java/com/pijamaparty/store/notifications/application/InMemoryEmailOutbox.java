package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.EmailStatus;
import com.pijamaparty.store.notifications.domain.EmailType;
import com.pijamaparty.store.notifications.domain.OutboxEmail;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Fake of {@link EnqueueEmails}, {@link LockDueEmails}, {@link RecordEmailOutcomes} and {@link EmailStatuses} over
 * the same outbox.
 */
public class InMemoryEmailOutbox implements EnqueueEmails, LockDueEmails, RecordEmailOutcomes, EmailStatuses {

    /** An email of the outbox as the table keeps it. */
    public record Stored(UUID id, EmailType type, UUID reference, String status, int attempts, Instant nextAttemptAt,
                         Optional<String> providerMessageId, Optional<Instant> sentAt) {

        Stored with(String newStatus, int newAttempts, Instant newNextAttemptAt, Optional<String> messageId,
                    Optional<Instant> newSentAt) {
            return new Stored(id, type, reference, newStatus, newAttempts, newNextAttemptAt, messageId, newSentAt);
        }
    }

    private static final Comparator<Stored> OLDEST_DUE_FIRST = Comparator.comparing(Stored::nextAttemptAt)
        .thenComparing(stored -> stored.id().toString());

    private final Map<UUID, Stored> emails = new LinkedHashMap<>();

    /** Test inspection: empty when the outbox has no such email. */
    public synchronized Optional<Stored> find(EmailType type, UUID reference) {
        return emails.values().stream()
            .filter(stored -> stored.type() == type && stored.reference().equals(reference))
            .findFirst();
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
        emails.put(id, new Stored(id, type, reference, "PENDING", 0, at, Optional.empty(), Optional.empty()));
        return true;
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
        return find(type, reference).map(stored -> EmailStatus.valueOf(stored.status()));
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
    public synchronized void failed(UUID email, Instant at) {
        Stored stored = emails.get(email);
        if (stored != null && stored.status().equals("PENDING")) {
            emails.put(email, stored.with("FAILED", stored.attempts(), stored.nextAttemptAt(), Optional.empty(),
                Optional.empty()));
        }
    }
}
