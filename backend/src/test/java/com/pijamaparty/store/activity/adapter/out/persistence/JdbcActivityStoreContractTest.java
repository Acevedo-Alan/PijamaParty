package com.pijamaparty.store.activity.adapter.out.persistence;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.activity.api.ActivityArea;
import com.pijamaparty.store.activity.application.ActivityPages;
import com.pijamaparty.store.activity.application.ActivityStoreContractTest;
import com.pijamaparty.store.activity.application.RecordedActivity;
import com.pijamaparty.store.activity.application.SaveActivity;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Each call committed in its own transaction as the administrator, so Row Level Security of V41 applies as in
 * production (RNF-27), then read back from PostgreSQL.
 */
@ApiTest
class JdbcActivityStoreContractTest extends ActivityStoreContractTest<JdbcActivityStoreContractTest.AsAdmin> {

    private final AsAdmin asAdmin;
    private final JdbcTemplate owner;

    JdbcActivityStoreContractTest(JdbcActivityStore adapter, PlatformTransactionManager transactionManager,
                                  PostgreSQLContainer<?> postgres) {
        this.asAdmin = new AsAdmin(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsAdmin createSubject() {
        return asAdmin;
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate activity_entry");
    }

    record AsAdmin(JdbcActivityStore adapter, TransactionTemplate transactions) implements SaveActivity, ActivityPages {

        @Override
        public void save(RecordedActivity activity) {
            asAdmin(() -> {
                adapter.save(activity);
                return null;
            });
        }

        @Override
        public List<RecordedActivity> page(Optional<ActivityArea> area, Optional<Instant> from,
                                           Optional<Instant> before, int offset, int limit) {
            return asAdmin(() -> adapter.page(area, from, before, offset, limit));
        }

        private <R> R asAdmin(Supplier<R> work) {
            return CurrentActor.callAs(RlsPolicyTester.ADMIN, () -> transactions.execute(status -> work.get()));
        }
    }
}
