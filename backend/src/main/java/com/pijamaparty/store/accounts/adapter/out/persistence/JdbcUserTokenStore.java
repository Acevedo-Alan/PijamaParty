package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.accounts.application.ConsumeUserTokens;
import com.pijamaparty.store.accounts.application.IssueUserTokens;
import com.pijamaparty.store.accounts.domain.UserToken;
import com.pijamaparty.store.accounts.domain.UserTokenPurpose;
import com.pijamaparty.store.platform.domain.AccessToken.AccountToken;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code user_token} (V29), which only {@code SYSTEM} reads and writes. Issuing joins the caller's transaction (the
 * email sending's). Consuming runs its own, as {@code SYSTEM} bound to the token's hash, so the database lets the
 * consumer change only that token's user (RNF-27); the token row is locked, so two openings of one link are taken
 * one after the other and only the first uses it.
 */
@Repository
class JdbcUserTokenStore implements IssueUserTokens, ConsumeUserTokens {

    private static final String INSERT = """
        INSERT INTO user_token (id, user_id, purpose, token_hash, created_at, expires_at)
        VALUES (:id, :userId, :purpose, :tokenHash, :createdAt, :expiresAt)
        """;

    private static final String LOCK_BY_HASH = """
        SELECT id, user_id, purpose, token_hash, created_at, expires_at, used_at FROM user_token
        WHERE token_hash = :tokenHash
        FOR UPDATE
        """;

    // A token only changes from unused to used (user_token_update_guard).
    private static final String MARK_USED = """
        UPDATE user_token SET used_at = :usedAt WHERE id = :id AND used_at IS NULL
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    JdbcUserTokenStore(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void add(UserToken token) {
        jdbc.update(INSERT, new MapSqlParameterSource("id", token.id())
            .addValue("userId", token.userId().value())
            .addValue("purpose", token.purpose().name())
            .addValue("tokenHash", token.tokenHash())
            .addValue("createdAt", token.createdAt().atOffset(ZoneOffset.UTC))
            .addValue("expiresAt", token.expiresAt().atOffset(ZoneOffset.UTC)));
    }

    @Override
    public <R> R consuming(AccountToken token, Function<Optional<UserToken>, R> consume) {
        return CurrentActor.callAsSystemConsuming(token, () -> transactions.execute(status -> {
            Optional<UserToken> found = jdbc.query(LOCK_BY_HASH,
                    new MapSqlParameterSource("tokenHash", token.hash()), (row, n) -> UserToken.restore(
                        row.getObject("id", UUID.class),
                        UserId.of(row.getObject("user_id", UUID.class)),
                        UserTokenPurpose.valueOf(row.getString("purpose")),
                        row.getString("token_hash"),
                        row.getObject("created_at", OffsetDateTime.class).toInstant(),
                        row.getObject("expires_at", OffsetDateTime.class).toInstant(),
                        Optional.ofNullable(row.getObject("used_at", OffsetDateTime.class))
                            .map(OffsetDateTime::toInstant)
                            .orElse(null)))
                .stream()
                .findFirst();
            return consume.apply(found);
        }));
    }

    @Override
    public void markUsed(UserToken token) {
        var parameters = new MapSqlParameterSource("id", token.id())
            .addValue("usedAt", token.usedAt().orElseThrow().atOffset(ZoneOffset.UTC));
        if (jdbc.update(MARK_USED, parameters) != 1) {
            throw new IllegalStateException("the token was not unused");
        }
    }
}
