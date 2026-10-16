package com.pijamaparty.store.platform.persistence;

import com.pijamaparty.store.platform.domain.AccessToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sets the Row Level Security context at the start of every physical transaction, read-only and programmatic ones
 * included (RNF-27): {@code app.role}, {@code app.user_id} and the SHA-256 of the cart, tracking and account
 * tokens, with {@code set_config(..., true)} so the values die with the transaction and never leak to the next user of the pooled
 * connection. Without a bound actor every value is empty and the policies return no rows. If the context cannot be
 * set, the transaction is rolled back and every resource released before failing, so the pool never loses the
 * connection and the thread is not left inside a dead transaction.
 */
class RlsTransactionManager extends JpaTransactionManager {

    private static final long serialVersionUID = 1L;

    // Positional parameters: this runs on the raw JDBC connection, where named parameters are not available.
    private static final String SET_CONTEXT = """
        select set_config('app.role', ?, true),
               set_config('app.user_id', ?, true),
               set_config('app.cart_token_hash', ?, true),
               set_config('app.tracking_token_hash', ?, true),
               set_config('app.account_token_hash', ?, true)
        """;

    RlsTransactionManager(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        try {
            DataSource dataSource = getDataSource();
            if (dataSource == null
                || !(TransactionSynchronizationManager.getResource(dataSource) instanceof ConnectionHolder holder)) {
                throw new CannotCreateTransactionException("No JDBC connection exposed: cannot set the RLS context");
            }
            applyContext(holder.getConnection(), CurrentActor.get());
        } catch (SQLException | RuntimeException e) {
            abandon(transaction);
            throw e instanceof CannotCreateTransactionException cannot ? cannot
                : new CannotCreateTransactionException("Could not set the RLS context", e);
        }
    }

    /** Overridable only to simulate a failure in tests. */
    void applyContext(Connection connection, Optional<Actor> actor) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SET_CONTEXT)) {
            statement.setString(1, actor.map(a -> a.role().name()).orElse(""));
            statement.setString(2, actor.flatMap(Actor::userId).map(UserId::toString).orElse(""));
            statement.setString(3, actor.flatMap(Actor::cartToken).map(AccessToken::hash).orElse(""));
            statement.setString(4, actor.flatMap(Actor::trackingToken).map(AccessToken::hash).orElse(""));
            statement.setString(5, actor.flatMap(Actor::accountToken).map(AccessToken::hash).orElse(""));
            statement.execute();
        }
    }

    /** Undoes what super.doBegin bound: the caller never gets a transaction, so nobody else will. */
    private void abandon(Object transaction) {
        try {
            if (TransactionSynchronizationManager.getResource(obtainEntityManagerFactory())
                instanceof EntityManagerHolder holder) {
                EntityTransaction entityTransaction = holder.getEntityManager().getTransaction();
                if (entityTransaction.isActive()) {
                    entityTransaction.rollback();
                }
            }
        } catch (RuntimeException rollbackFailure) {
            logger.debug("Rollback after a failed RLS context failed too", rollbackFailure);
        } finally {
            doCleanupAfterCompletion(transaction);
        }
    }
}
