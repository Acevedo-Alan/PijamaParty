package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.accounts.application.CustomerRegistrations;
import com.pijamaparty.store.accounts.domain.PasswordHash;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * UC-19: registration happens before anyone is signed in, so it runs in its own transaction as {@code SYSTEM}, which
 * may only insert unverified customers with a password (V29). The email's unique constraint decides between two
 * registrations of one email: the loser inserts nothing, without aborting its transaction.
 */
@Repository
class JdbcCustomerRegistrations implements CustomerRegistrations {

    private static final String INSERT = """
        INSERT INTO app_user (id, email, email_verified, password_hash, name, role, failed_login_attempts,
                              created_at, updated_at)
        VALUES (:id, :email, false, :passwordHash, :name, 'CUSTOMER', 0, :createdAt, :updatedAt)
        ON CONFLICT (email) DO NOTHING
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    JdbcCustomerRegistrations(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public boolean register(User customer, Runnable whenRegistered) {
        if (customer.role() != UserRole.CUSTOMER || customer.emailVerified()) {
            throw new IllegalArgumentException("only unverified customers register");
        }
        var parameters = new MapSqlParameterSource("id", customer.id().value())
            .addValue("email", customer.email().value())
            .addValue("passwordHash", customer.passwordHash().map(PasswordHash::value).orElseThrow())
            .addValue("name", customer.name())
            .addValue("createdAt", customer.createdAt().atOffset(ZoneOffset.UTC))
            .addValue("updatedAt", customer.updatedAt().atOffset(ZoneOffset.UTC));
        return CurrentActor.callAsSystem(() -> transactions.execute(status -> {
            if (jdbc.update(INSERT, parameters) == 0) {
                return false;
            }
            whenRegistered.run();
            return true;
        }));
    }
}
