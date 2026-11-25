package com.pijamaparty.store.backoffice.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.backoffice.application.LoadOmittedSetupItems;
import com.pijamaparty.store.backoffice.application.OmittedSetupItemsContractTest;
import com.pijamaparty.store.backoffice.application.SaveOmittedSetupItems;
import com.pijamaparty.store.backoffice.domain.SetupItem;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Table {@code setup_guide_omission} (V46) through the adapter, each call in its own committed transaction (UC-46). */
@ApiTest
class JdbcSetupGuideOmissionsContractTest
    extends OmittedSetupItemsContractTest<JdbcSetupGuideOmissionsContractTest.InTransaction> {

    private final InTransaction inTransaction;
    private final JdbcTemplate owner;

    JdbcSetupGuideOmissionsContractTest(JdbcSetupGuideOmissions adapter, PlatformTransactionManager transactionManager,
                                        PostgreSQLContainer<?> postgres) {
        this.inTransaction = new InTransaction(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected InTransaction createSubject() {
        return inTransaction;
    }

    @BeforeEach
    @AfterEach
    void cleanUp() {
        owner.execute("truncate setup_guide_omission");
    }

    @Test
    void givenAnItemAWorkingStoreNeeds_whenStoredAnyway_thenTheTableRefusesIt() {
        assertThatThrownBy(() -> subject.omit(SetupItem.WHATSAPP, Instant.parse("2026-11-24T15:00:00Z")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Each call in its own transaction, as a use case would run it. */
    record InTransaction(JdbcSetupGuideOmissions adapter, TransactionTemplate transactions)
        implements LoadOmittedSetupItems, SaveOmittedSetupItems {

        @Override
        public Set<SetupItem> omitted() {
            return transactions.execute(status -> adapter.omitted());
        }

        @Override
        public void omit(SetupItem item, Instant at) {
            transactions.executeWithoutResult(status -> adapter.omit(item, at));
        }

        @Override
        public void show(SetupItem item) {
            transactions.executeWithoutResult(status -> adapter.show(item));
        }
    }
}
