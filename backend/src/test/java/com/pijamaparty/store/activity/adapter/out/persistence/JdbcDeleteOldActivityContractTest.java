package com.pijamaparty.store.activity.adapter.out.persistence;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.activity.application.DeleteOldActivity;
import com.pijamaparty.store.activity.application.DeleteOldActivityContractTest;
import com.pijamaparty.store.activity.application.RecordedActivity;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The deletion committed as the system, the role of the retention job, so Row Level Security of V41 applies as in
 * production (RNF-27). The entries are stored and counted as the owner.
 */
@ApiTest
class JdbcDeleteOldActivityContractTest
    extends DeleteOldActivityContractTest<JdbcDeleteOldActivityContractTest.AsSystem> {

    private final AsSystem asSystem;
    private final JdbcTemplate owner;

    JdbcDeleteOldActivityContractTest(JdbcActivityStore adapter, PlatformTransactionManager transactionManager,
                                      PostgreSQLContainer<?> postgres) {
        this.asSystem = new AsSystem(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected AsSystem createSubject() {
        return asSystem;
    }

    @Override
    protected void stored(RecordedActivity activity) {
        owner.update("""
            insert into activity_entry (id, occurred_at, actor_id, area, action, subject_type, subject_id,
                                        subject_label, changes)
            values (?, ?, ?, ?, ?, ?, ?, ?, '[]'::jsonb)
            """, activity.id(), Timestamp.from(activity.occurredAt()), activity.entry().administrator().value(),
            activity.entry().action().area().name(), activity.entry().action().name(),
            activity.entry().subject().type().name(), activity.entry().subject().id().orElse(null),
            activity.entry().subject().label().orElse(null));
    }

    @Override
    protected Set<UUID> remaining() {
        return new HashSet<>(owner.queryForList("select id from activity_entry", UUID.class));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate activity_entry");
    }

    record AsSystem(JdbcActivityStore adapter, TransactionTemplate transactions) implements DeleteOldActivity {

        @Override
        public int deleteOccurredBefore(Instant cutoff) {
            return CurrentActor.callAsSystem(
                () -> transactions.execute(status -> adapter.deleteOccurredBefore(cutoff)));
        }
    }
}
