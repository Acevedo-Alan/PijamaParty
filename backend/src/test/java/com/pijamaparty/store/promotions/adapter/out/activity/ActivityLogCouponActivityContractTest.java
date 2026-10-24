package com.pijamaparty.store.promotions.adapter.out.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.testing.ActivityRows;
import com.pijamaparty.store.promotions.application.CouponActivity;
import com.pijamaparty.store.promotions.application.CouponActivityContractTest;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Through the activity module's public API to PostgreSQL, in the transaction of a change. */
@ApiTest
class ActivityLogCouponActivityContractTest extends CouponActivityContractTest<ActivityLogCouponActivityContractTest.InTransaction> {

    private final ActivityLogCouponActivity adapter;
    private final TransactionTemplate transactions;
    private final ActivityRows rows;

    ActivityLogCouponActivityContractTest(ActivityLogCouponActivity adapter, PlatformTransactionManager transactionManager,
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

    /** The module records in the transaction of its change, as the actor that makes it. */
    record InTransaction(ActivityLogCouponActivity adapter, TransactionTemplate transactions) implements CouponActivity {

        @Override
        public void record(ActivityEntry entry) {
            CurrentActor.runAs(RlsPolicyTester.ADMIN, () -> transactions.executeWithoutResult(status -> adapter.record(entry)));
        }
    }
}
