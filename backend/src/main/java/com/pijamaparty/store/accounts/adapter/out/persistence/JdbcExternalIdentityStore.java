package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.accounts.application.FindExternalIdentities;
import com.pijamaparty.store.accounts.application.LinkExternalIdentities;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code external_identity} (V34), in the caller's transaction, which runs as {@code SYSTEM}: only the system reads them
 * to authenticate and links them, only to customers. Either unique constraint decides between two links racing: the
 * loser inserts nothing, without aborting the transaction.
 */
@Repository
class JdbcExternalIdentityStore implements FindExternalIdentities, LinkExternalIdentities {

    private static final String FIND = """
        SELECT id, user_id, provider, subject, email, created_at FROM external_identity
        WHERE provider = :provider AND subject = :subject
        """;

    private static final String HAS_PROVIDER = """
        SELECT EXISTS (SELECT 1 FROM external_identity WHERE user_id = :userId AND provider = :provider)
        """;

    private static final String INSERT = """
        INSERT INTO external_identity (id, user_id, provider, subject, email, created_at)
        VALUES (:id, :userId, :provider, :subject, :email, :createdAt)
        ON CONFLICT DO NOTHING
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcExternalIdentityStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ExternalIdentity> find(ExternalAccount account) {
        var parameters = new MapSqlParameterSource("provider", account.provider().name())
            .addValue("subject", account.subject());
        return jdbc.query(FIND, parameters, (row, n) -> new ExternalIdentity(
                row.getObject("id", UUID.class),
                UserId.of(row.getObject("user_id", UUID.class)),
                new ExternalAccount(ExternalProvider.valueOf(row.getString("provider")), row.getString("subject")),
                new Email(row.getString("email")),
                row.getObject("created_at", OffsetDateTime.class).toInstant()))
            .stream()
            .findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean hasProvider(UserId user, ExternalProvider provider) {
        var parameters = new MapSqlParameterSource("userId", user.value()).addValue("provider", provider.name());
        return Boolean.TRUE.equals(jdbc.queryForObject(HAS_PROVIDER, parameters, Boolean.class));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean link(ExternalIdentity identity) {
        return jdbc.update(INSERT, new MapSqlParameterSource("id", identity.id())
            .addValue("userId", identity.userId().value())
            .addValue("provider", identity.account().provider().name())
            .addValue("subject", identity.account().subject())
            .addValue("email", identity.email().value())
            .addValue("createdAt", identity.createdAt().atOffset(ZoneOffset.UTC))) == 1;
    }
}
