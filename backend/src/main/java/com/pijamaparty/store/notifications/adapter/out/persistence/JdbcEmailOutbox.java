package com.pijamaparty.store.notifications.adapter.out.persistence;

import com.pijamaparty.store.notifications.application.EmailStatuses;
import com.pijamaparty.store.notifications.application.EnqueueEmails;
import com.pijamaparty.store.notifications.application.LockDueEmails;
import com.pijamaparty.store.notifications.application.RecordEmailOutcomes;
import com.pijamaparty.store.notifications.domain.EmailStatus;
import com.pijamaparty.store.notifications.domain.EmailType;
import com.pijamaparty.store.notifications.domain.OutboxEmail;
import com.pijamaparty.store.platform.application.IdGenerator;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The outbox in table {@code email_outbox} (V19), as {@code SYSTEM} (RNF-27). Enqueueing and locking join the
 * caller's transaction; recording joins it or, when there is none, runs in its own short one, so no connection is
 * held while an email is sent (RNF-21).
 */
@Repository
class JdbcEmailOutbox implements EnqueueEmails, LockDueEmails, RecordEmailOutcomes, EmailStatuses {

    // RNF-20.7: each type of email once per reference; a repeated event inserts nothing.
    private static final String INSERT = """
        INSERT INTO email_outbox (id, type, reference, status, attempts, next_attempt_at, created_at, updated_at)
        VALUES (:id, :type, :reference, 'PENDING', 0, :at, :at, :at)
        ON CONFLICT (type, reference) DO NOTHING
        """;

    // Oldest due first, over the partial index email_outbox_due_idx. SKIP LOCKED lets two runs work side by side
    // without taking the same email.
    private static final String LOCK_NEXT_DUE = """
        SELECT id, type, reference, attempts FROM email_outbox
        WHERE status = 'PENDING' AND next_attempt_at <= :now
        ORDER BY next_attempt_at, id
        LIMIT 1
        FOR UPDATE SKIP LOCKED
        """;

    // Only a pending email changes: a late outcome never rewrites one already recorded.
    private static final String ATTEMPT = """
        UPDATE email_outbox SET attempts = :attempts, next_attempt_at = :retryAt, updated_at = :at
        WHERE id = :id AND status = 'PENDING'
        """;

    private static final String SENT = """
        UPDATE email_outbox SET status = 'SENT', provider_message_id = :messageId, sent_at = :at, updated_at = :at
        WHERE id = :id AND status = 'PENDING'
        """;

    private static final String FAILED = """
        UPDATE email_outbox SET status = 'FAILED', updated_at = :at
        WHERE id = :id AND status = 'PENDING'
        """;

    // UC-18: the administrator reads the outbox for the back-office alert (email_outbox_admin_select).
    private static final String SELECT_STATUS = """
        SELECT status FROM email_outbox WHERE type = :type AND reference = :reference
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final IdGenerator ids;

    JdbcEmailOutbox(NamedParameterJdbcTemplate jdbc, IdGenerator ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean enqueue(EmailType type, UUID reference, Instant at) {
        return jdbc.update(INSERT, new MapSqlParameterSource("id", ids.newId())
            .addValue("type", type.name())
            .addValue("reference", reference)
            .addValue("at", at.atOffset(ZoneOffset.UTC))) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<OutboxEmail> lockNextDue(Instant now) {
        return jdbc.query(LOCK_NEXT_DUE, new MapSqlParameterSource("now", now.atOffset(ZoneOffset.UTC)),
                (row, n) -> new OutboxEmail(row.getObject("id", UUID.class), EmailType.valueOf(row.getString("type")),
                    row.getObject("reference", UUID.class), row.getInt("attempts")))
            .stream().findFirst();
    }

    @Override
    public Optional<EmailStatus> statusOf(EmailType type, UUID reference) {
        return jdbc.queryForList(SELECT_STATUS, new MapSqlParameterSource("type", type.name())
                .addValue("reference", reference), String.class)
            .stream().findFirst().map(EmailStatus::valueOf);
    }

    @Override
    @Transactional
    public void attempting(UUID email, int attempts, Instant retryAt, Instant at) {
        jdbc.update(ATTEMPT, new MapSqlParameterSource("id", email)
            .addValue("attempts", attempts)
            .addValue("retryAt", retryAt.atOffset(ZoneOffset.UTC))
            .addValue("at", at.atOffset(ZoneOffset.UTC)));
    }

    @Override
    @Transactional
    public void sent(UUID email, String providerMessageId, Instant at) {
        jdbc.update(SENT, new MapSqlParameterSource("id", email)
            .addValue("messageId", providerMessageId)
            .addValue("at", at.atOffset(ZoneOffset.UTC)));
    }

    @Override
    @Transactional
    public void failed(UUID email, Instant at) {
        jdbc.update(FAILED, new MapSqlParameterSource("id", email).addValue("at", at.atOffset(ZoneOffset.UTC)));
    }
}
