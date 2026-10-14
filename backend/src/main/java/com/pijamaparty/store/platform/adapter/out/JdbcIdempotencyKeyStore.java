package com.pijamaparty.store.platform.adapter.out;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.platform.application.ExpiredIdempotencyKeys;
import com.pijamaparty.store.platform.application.IdempotencyKeys;
import com.pijamaparty.store.platform.domain.IdempotencyScope;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@code idempotency_key} (V21, T-E3-14). Every statement joins the caller's transaction, whose actor Row Level
 * Security checks against the scope (RNF-27).
 */
@Repository
class JdbcIdempotencyKeyStore implements IdempotencyKeys, ExpiredIdempotencyKeys {

    // RNF-20.2: a transaction-level advisory lock, released when the claiming transaction ends. A first request still
    // in flight holds it, so a second one is told so at once instead of waiting on the primary key. Two different keys
    // sharing a 64-bit hash would only make one of them answer 409 once.
    private static final String TRY_LOCK = """
        SELECT pg_try_advisory_xact_lock(hashtextextended(:lock, 0))
        """;

    private static final String SELECT_KEY = """
        SELECT request_hash, resource_id, response_status, response_body::text AS response_body
        FROM idempotency_key
        WHERE scope = :scope AND idempotency_key = :key
        """;

    private static final String INSERT_KEY = """
        INSERT INTO idempotency_key (scope, idempotency_key, request_hash, resource_id, response_status, response_body,
                                     created_at, expires_at)
        VALUES (:scope, :key, :requestHash, :resourceId, :status, CAST(:body AS jsonb), :createdAt, :expiresAt)
        """;

    // Only SYSTEM reaches every key (RNF-27); idempotency_key_expires_at_idx finds them.
    private static final String DELETE_EXPIRED = """
        DELETE FROM idempotency_key
        WHERE expires_at <= :now
        """;

    private static final TypeReference<Map<String, String>> BODY = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    JdbcIdempotencyKeyStore(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Claim claim(IdempotencyScope scope, UUID key) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            // Outside a transaction the lock would be released as soon as it is taken.
            throw new IllegalStateException("a key is claimed within the caller's transaction");
        }
        var params = new MapSqlParameterSource("scope", scope.value()).addValue("key", key);
        Boolean locked = jdbc.queryForObject(TRY_LOCK, new MapSqlParameterSource("lock", scope.value() + "/" + key),
            Boolean.class);
        if (!Boolean.TRUE.equals(locked)) {
            return new Claim.Busy();
        }
        return jdbc.query(SELECT_KEY, params, (row, n) -> new StoredAnswer(row.getString("request_hash"),
                row.getObject("resource_id", UUID.class), row.getInt("response_status"),
                read(row.getString("response_body"))))
            .stream().findFirst()
            .<Claim>map(Claim.Answered::new)
            .orElseGet(Claim.Fresh::new);
    }

    @Override
    public void record(IdempotencyScope scope, UUID key, StoredAnswer answer, Instant createdAt, Instant expiresAt) {
        jdbc.update(INSERT_KEY, new MapSqlParameterSource("scope", scope.value())
            .addValue("key", key)
            .addValue("requestHash", answer.requestHash())
            .addValue("resourceId", answer.resourceId())
            .addValue("status", answer.status())
            .addValue("body", write(answer.body()))
            .addValue("createdAt", createdAt.atOffset(ZoneOffset.UTC))
            .addValue("expiresAt", expiresAt.atOffset(ZoneOffset.UTC)));
    }

    @Override
    public int deleteExpiredAt(Instant now) {
        return jdbc.update(DELETE_EXPIRED, new MapSqlParameterSource("now", now.atOffset(ZoneOffset.UTC)));
    }

    private String write(Map<String, String> body) {
        try {
            return json.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A map of strings is always JSON", e);
        }
    }

    private Map<String, String> read(String body) {
        try {
            return json.readValue(body, BODY);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A stored answer is a JSON object of strings", e);
        }
    }
}
