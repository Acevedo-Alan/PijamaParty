package com.pijamaparty.store.activity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivityLog;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-37: {@link ActivityLog} writes in the transaction of the change, on PostgreSQL. The change and its entry commit
 * together or not at all, and a call outside a transaction fails instead of committing an entry on its own.
 */
@ApiTest
class ActivityLogTransactionTest {

    private static final ActivityEntry HIDDEN = new ActivityEntry(RlsPolicyTester.ADMIN_ID,
        ActivityAction.PRODUCT_HIDDEN, ActivitySubject.of(SubjectType.PRODUCT, UUID.randomUUID(), "Pijama Luna"));

    private final ActivityLog log;
    private final TransactionTemplate transactions;
    private final JdbcTemplate owner;

    ActivityLogTransactionTest(ActivityLog log, PlatformTransactionManager transactionManager,
                               PostgreSQLContainer<?> postgres) {
        this.log = log;
        this.transactions = new TransactionTemplate(transactionManager);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate activity_entry");
    }

    @Test
    void givenAChangeThatCommits_whenItRecordsItsEntry_thenTheEntryIsStored() {
        CurrentActor.runAs(RlsPolicyTester.ADMIN, () -> transactions.executeWithoutResult(status -> log.record(HIDDEN)));

        assertThat(owner.queryForObject("select action from activity_entry", String.class)).isEqualTo("PRODUCT_HIDDEN");
    }

    @Test
    void givenAChangeThatFailsAfterRecordingItsEntry_whenItRollsBack_thenTheEntryIsNotStored() {
        CurrentActor.runAs(RlsPolicyTester.ADMIN, () -> transactions.executeWithoutResult(status -> {
            log.record(HIDDEN);
            status.setRollbackOnly();
        }));

        assertThat(owner.queryForObject("select count(*) from activity_entry", Integer.class)).isZero();
    }

    @Test
    void givenNoTransaction_whenAnEntryIsRecorded_thenItFails() {
        assertThatThrownBy(() -> CurrentActor.runAs(RlsPolicyTester.ADMIN, () -> log.record(HIDDEN)))
            .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(owner.queryForObject("select count(*) from activity_entry", Integer.class)).isZero();
    }
}
