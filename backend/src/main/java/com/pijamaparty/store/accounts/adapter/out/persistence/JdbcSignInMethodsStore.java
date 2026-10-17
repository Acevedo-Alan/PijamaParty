package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.accounts.application.LoadSignInMethods;
import com.pijamaparty.store.accounts.application.UnlinkExternalIdentities;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalIdentity;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.accounts.domain.SignInMethods;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-27 (T-E4-15): a customer's password and external accounts ({@code app_user}, {@code external_identity}, V34), in
 * the caller's transaction, which runs as that customer: Row Level Security shows it only its own account and
 * external accounts, and lets it unlink only its own (RNF-27); the {@code WHERE} says whose they are.
 */
@Repository
class JdbcSignInMethodsStore implements LoadSignInMethods, UnlinkExternalIdentities {

    private static final String HAS_PASSWORD = """
        SELECT password_hash IS NOT NULL
        FROM app_user
        WHERE id = :userId
        """;

    // The account row is the lock of its sign-in methods, as of its address book (UC-20).
    private static final String LOCK_HAS_PASSWORD = """
        SELECT password_hash IS NOT NULL
        FROM app_user
        WHERE id = :userId
        FOR UPDATE
        """;

    private static final String SELECT_IDENTITIES = """
        SELECT id, provider, subject, email, created_at
        FROM external_identity
        WHERE user_id = :userId
        """;

    private static final String DELETE_IDENTITY = """
        DELETE FROM external_identity
        WHERE id = :id AND user_id = :userId
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcSignInMethodsStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<SignInMethods> findByCustomer(UserId customer) {
        return methods(customer, HAS_PASSWORD);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<SignInMethods> lockByCustomer(UserId customer) {
        return methods(customer, LOCK_HAS_PASSWORD);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void unlink(ExternalIdentity identity) {
        jdbc.update(DELETE_IDENTITY, new MapSqlParameterSource("id", identity.id())
            .addValue("userId", identity.userId().value()));
    }

    private Optional<SignInMethods> methods(UserId customer, String accountQuery) {
        var parameters = new MapSqlParameterSource("userId", customer.value());
        List<Boolean> hasPassword = jdbc.queryForList(accountQuery, parameters, Boolean.class);
        if (hasPassword.isEmpty()) {
            return Optional.empty();
        }
        List<ExternalIdentity> identities = jdbc.query(SELECT_IDENTITIES, parameters, (row, n) ->
            new ExternalIdentity(
                row.getObject("id", UUID.class),
                customer,
                new ExternalAccount(ExternalProvider.valueOf(row.getString("provider")), row.getString("subject")),
                new Email(row.getString("email")),
                row.getObject("created_at", OffsetDateTime.class).toInstant()));
        return Optional.of(new SignInMethods(customer, Boolean.TRUE.equals(hasPassword.getFirst()), identities));
    }
}
