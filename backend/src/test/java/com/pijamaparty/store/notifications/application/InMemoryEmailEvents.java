package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.EmailEventType;
import com.pijamaparty.store.notifications.domain.MailProvider;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fake of {@link EmailEvents}: one notification per provider and event id, as the primary key. Transactions are not
 * modeled: a recorded notification stays recorded.
 */
public class InMemoryEmailEvents implements EmailEvents {

    /** What the table would hold of a notification. */
    public record Recorded(MailProvider provider, String eventId, EmailEventType type, String providerMessageId) {}

    private record Key(MailProvider provider, String eventId) {}

    private final Map<Key, Recorded> recorded = new LinkedHashMap<>();

    @Override
    public synchronized boolean recordFirst(MailProvider provider, String eventId, EmailEventType type,
                                            String providerMessageId, Instant receivedAt) {
        return recorded.putIfAbsent(new Key(provider, eventId),
            new Recorded(provider, eventId, type, providerMessageId)) == null;
    }

    public synchronized List<Recorded> recorded() {
        return List.copyOf(recorded.values());
    }
}
