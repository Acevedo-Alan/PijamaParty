package com.pijamaparty.store.catalog.adapter.out.activity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.catalog.application.CatalogActivity;
import com.pijamaparty.store.catalog.application.CatalogActivityContractTest;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.platform.testing.RlsPolicyTester;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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
    private final JdbcTemplate owner;
    private final ObjectMapper json;

    ActivityLogCatalogActivityContractTest(ActivityLogCatalogActivity adapter,
                                           PlatformTransactionManager transactionManager,
                                           PostgreSQLContainer<?> postgres, ObjectMapper json) {
        this.adapter = adapter;
        this.transactions = new TransactionTemplate(transactionManager);
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
        this.json = json;
    }

    @Override
    protected InTransaction createSubject() {
        return new InTransaction(adapter, transactions);
    }

    @Override
    protected List<ActivityEntry> recorded() {
        return owner.query("""
            select actor_id, action, subject_type, subject_id, subject_label, changes::text as changes
            from activity_entry
            """, (row, index) -> new ActivityEntry(UserId.of(row.getObject("actor_id", UUID.class)),
            ActivityAction.valueOf(row.getString("action")),
            new ActivitySubject(SubjectType.valueOf(row.getString("subject_type")),
                Optional.ofNullable(row.getObject("subject_id", UUID.class)),
                Optional.ofNullable(row.getString("subject_label"))),
            changes(row.getString("changes"))));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate activity_entry");
    }

    @Test
    void givenNoTransaction_whenRecording_thenItIsRefused() {
        ActivityEntry entry = new ActivityEntry(RlsPolicyTester.ADMIN_ID, ActivityAction.PRODUCT_PUBLISHED,
            ActivitySubject.of(SubjectType.PRODUCT, UUID.randomUUID(), "Pijama Luna"));

        assertThatThrownBy(() -> CurrentActor.runAs(RlsPolicyTester.ADMIN, () -> adapter.record(entry)))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    private List<FieldChange> changes(String text) {
        try {
            List<FieldChange> changes = new ArrayList<>();
            for (JsonNode change : json.readTree(text)) {
                changes.add(new FieldChange(change.get("field").asText(), value(change.get("before")),
                    value(change.get("after"))));
            }
            return changes;
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Optional<String> value(JsonNode node) {
        return node == null || node.isNull() ? Optional.empty() : Optional.of(node.asText());
    }

    /** The catalog records in the transaction of its change, as the administrator who made it. */
    record InTransaction(ActivityLogCatalogActivity adapter, TransactionTemplate transactions)
        implements CatalogActivity {

        private static final Actor ADMIN = RlsPolicyTester.ADMIN;

        @Override
        public void record(ActivityEntry entry) {
            CurrentActor.runAs(ADMIN, () -> transactions.executeWithoutResult(status -> adapter.record(entry)));
        }
    }
}
