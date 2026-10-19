package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.accounts.application.LoadPendingLinks;
import com.pijamaparty.store.accounts.application.SavePendingLinks;
import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.accounts.domain.PendingLink;
import com.pijamaparty.store.platform.domain.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code pending_link} (V35), in the caller's transaction, which runs as {@code SYSTEM}: the provider's return keeps
 * the external account waiting, the email sending reads it and the confirmation forgets it.
 */
@Repository
class JdbcPendingLinkStore implements LoadPendingLinks, SavePendingLinks {

    private static final String FIND = """
        SELECT user_id, provider, subject, requested_at FROM pending_link WHERE user_id = :userId
        """;

    // The account row is the lock: the pending link has no row to lock before the first one exists.
    private static final String LOCK_BY_CUSTOMER = """
        SELECT p.user_id, p.provider, p.subject, p.requested_at
        FROM app_user u LEFT JOIN pending_link p ON p.user_id = u.id
        WHERE u.id = :userId
        FOR UPDATE OF u
        """;

    private static final String UPSERT = """
        INSERT INTO pending_link (user_id, provider, subject, requested_at)
        VALUES (:userId, :provider, :subject, :requestedAt)
        ON CONFLICT (user_id) DO UPDATE
            SET provider = EXCLUDED.provider, subject = EXCLUDED.subject, requested_at = EXCLUDED.requested_at
        """;

    private static final String DELETE = """
        DELETE FROM pending_link WHERE user_id = :userId
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcPendingLinkStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<PendingLink> find(UserId user) {
        return first(jdbc.query(FIND, byUser(user), JdbcPendingLinkStore::row));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<PendingLink> lockByCustomer(UserId user) {
        return first(jdbc.query(LOCK_BY_CUSTOMER, byUser(user), JdbcPendingLinkStore::row));
    }

    private static MapSqlParameterSource byUser(UserId user) {
        return new MapSqlParameterSource("userId", user.value());
    }

    /** The account without a pending link reads as a row of nulls: no pending link. */
    private static PendingLink row(ResultSet row, int n) throws SQLException {
        return row.getObject("user_id", UUID.class) == null ? null : new PendingLink(
            UserId.of(row.getObject("user_id", UUID.class)),
            new ExternalAccount(ExternalProvider.valueOf(row.getString("provider")), row.getString("subject")),
            row.getObject("requested_at", OffsetDateTime.class).toInstant());
    }

    private static Optional<PendingLink> first(List<PendingLink> rows) {
        return rows.stream().filter(Objects::nonNull).findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void request(PendingLink link) {
        jdbc.update(UPSERT, new MapSqlParameterSource("userId", link.userId().value())
            .addValue("provider", link.account().provider().name())
            .addValue("subject", link.account().subject())
            .addValue("requestedAt", link.requestedAt().atOffset(ZoneOffset.UTC)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void forget(UserId user) {
        jdbc.update(DELETE, new MapSqlParameterSource("userId", user.value()));
    }
}
