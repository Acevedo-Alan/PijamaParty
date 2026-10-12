package com.pijamaparty.store.platform.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs writes inside a savepoint of the caller's transaction, so a violated constraint becomes a port outcome and the
 * transaction stays usable (ARCHITECTURE.md section 4, JDBC adapters).
 */
public final class Savepoints {

    private static final String FOREIGN_KEY_VIOLATION = "23503";

    private final DataSource dataSource;

    public Savepoints(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Returns what {@code work} returns. When it breaks a constraint, only {@code work} is rolled back and
     * {@code onViolation} decides: its value is returned, or, when empty, the violation propagates. Any other failure
     * propagates.
     */
    public <T> T run(Supplier<T> work, Function<DataIntegrityViolationException, Optional<T>> onViolation) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("a savepoint is taken within the caller's transaction");
        }
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            Savepoint savepoint = connection.setSavepoint();
            try {
                T result = work.get();
                connection.releaseSavepoint(savepoint);
                return result;
            } catch (DataIntegrityViolationException violation) {
                connection.rollback(savepoint);
                return onViolation.apply(violation).orElseThrow(() -> violation);
            }
        } catch (SQLException e) {
            throw new TransactionSystemException("Could not use a savepoint", e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    public static boolean isForeignKeyViolation(DataIntegrityViolationException violation) {
        return violation.getMostSpecificCause() instanceof SQLException sql
            && FOREIGN_KEY_VIOLATION.equals(sql.getSQLState());
    }
}
