package com.pijamaparty.store.notifications.adapter.out.persistence;

import com.pijamaparty.store.notifications.application.EmailEvents;
import com.pijamaparty.store.notifications.domain.EmailEventType;
import com.pijamaparty.store.notifications.domain.MailProvider;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notifications in table {@code email_event} (V23), in the caller's transaction. Row Level Security lets only the
 * system write (RNF-27).
 */
@Repository
class JdbcEmailEventStore implements EmailEvents {

    // RNF-20.3: over email_event_pk. A concurrent insert of the same key waits for the other transaction and inserts
    // nothing once it commits, so no row comes back.
    private static final String INSERT_FIRST = """
        INSERT INTO email_event (provider, event_id, type, provider_message_id, received_at)
        VALUES (:provider, :eventId, :type, :messageId, :receivedAt)
        ON CONFLICT (provider, event_id) DO NOTHING
        RETURNING event_id
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcEmailEventStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordFirst(MailProvider provider, String eventId, EmailEventType type, String providerMessageId,
                               Instant receivedAt) {
        return !jdbc.queryForList(INSERT_FIRST, new MapSqlParameterSource("provider", provider.name())
                .addValue("eventId", eventId)
                .addValue("type", type.name())
                .addValue("messageId", providerMessageId)
                .addValue("receivedAt", receivedAt.atOffset(ZoneOffset.UTC)), String.class)
            .isEmpty();
    }
}
