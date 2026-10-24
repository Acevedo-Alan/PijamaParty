package com.pijamaparty.store.catalog.adapter.out.activity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.activity.testing.ActivityRows;
import com.pijamaparty.store.catalog.application.CatalogActivity;
import com.pijamaparty.store.catalog.application.CatalogActivityContractTest;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Through the activity module's public API to PostgreSQL, as the administrator, in the transaction of a change. */
@ApiTest
class ActivityLogCatalogActivityContractTest
    extends CatalogActivityContractTest<ActivityLogCatalogActivityContractTest.InTransaction> {

    private final ActivityLogCatalogActivity adapter;
    private final TransactionTemplate transactions;
    private final ActivityRows rows;

    ActivityLogCatalogActivityContractTest(ActivityLogCatalogActivity adapter,
                                           PlatformTransactionManager transactionManager,
                                           PostgreSQLContainer<?> postgres, ObjectMapper json) {
        this.adapter = adapter;
        this.transactions = new TransactionTemplate(transactionManager);
        this.rows = new ActivityRows(postgres, json);
    }

    @Override
    protected InTransaction createSubject() {
        return new InTransaction(adapter, transactions);
    }

    @Override
    protected List<ActivityEntry> recorded() {
        return rows.all();
    }

    // Other tests leave entries behind; this contract reads the whole table.
    @BeforeEach
    @AfterEach
    void emptyTheLog() {
        rows.deleteAll();
    }

    @Test
    void givenNoTransaction_whenRecording_thenItIsRefused() {
        ActivityEntry entry = new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.PRODUCT_PUBLISHED,
            ActivitySubject.of(SubjectType.PRODUCT, UUID.randomUUID(), "Pijama Luna"));

        assertThatThrownBy(() -> CurrentActor.runAs(RlsPolicyTester.ADMIN, () -> adapter.record(entry)))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    /** The catalog records in the transaction of its change, as the administrator who made it. */
    record InTransaction(ActivityLogCatalogActivity adapter, TransactionTemplate transactions)
        implements CatalogActivity {

        @Override
        public void record(ActivityEntry entry) {
            CurrentActor.runAs(RlsPolicyTester.ADMIN,
                () -> transactions.executeWithoutResult(status -> adapter.record(entry)));
        }
    }
}
