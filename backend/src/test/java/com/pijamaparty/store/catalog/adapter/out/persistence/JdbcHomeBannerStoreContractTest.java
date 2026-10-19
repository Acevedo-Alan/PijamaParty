package com.pijamaparty.store.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.catalog.application.HomeBannerStoreContractTest;
import com.pijamaparty.store.catalog.application.LoadHomeBanner;
import com.pijamaparty.store.catalog.application.SaveHomeBanner;
import com.pijamaparty.store.catalog.domain.HomeBanner;
import com.pijamaparty.store.platform.testing.ApiTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Every call of the contract is its own committed transaction, as a use case would run it, by the application role.
 * The row of V39 is put back hidden and empty around each test. The schema tests below check that V39 backs the
 * domain's rules on its own.
 */
@ApiTest
class JdbcHomeBannerStoreContractTest extends HomeBannerStoreContractTest<JdbcHomeBannerStoreContractTest.InTransaction> {

    /** Puts the row back as V39 creates it. */
    static final String RESET = """
        update home_banner
        set title = null, body = null, link_text = null, link_path = null, image_storage_key = null,
            image_placeholder = null, image_upload_key = null, image_content_sha256 = null, published = false
        """;

    private static final String SET_TEXTS = """
        update home_banner set title = 'Personajes', body = '', link_text = 'Ver', link_path = ?
        """;

    private final InTransaction inTransaction;
    private final JdbcTemplate app;
    private final JdbcTemplate owner;

    JdbcHomeBannerStoreContractTest(JdbcHomeBannerStore adapter, PlatformTransactionManager transactionManager,
                                    JdbcTemplate app, PostgreSQLContainer<?> postgres) {
        this.inTransaction = new InTransaction(adapter, new TransactionTemplate(transactionManager));
        this.app = app;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected InTransaction createSubject() {
        return inTransaction;
    }

    @BeforeEach
    @AfterEach
    void reset() {
        owner.update(RESET);
    }

    @Test
    void givenTheMigratedSchema_whenAnotherBannerIsInserted_thenThereIsStillOnlyOne() {
        assertThatThrownBy(() -> app.update("insert into home_banner (id, published, updated_at) values (2, false, now())"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(app.queryForObject("select count(*) from home_banner", Integer.class)).isOne();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://other.site", "//other.site", "/\\other.site", "other.site", " /", "/a\tb"})
    void givenATargetOutsideTheStore_whenStoredDirectly_thenTheSchemaRefusesIt(String target) {
        assertThatThrownBy(() -> app.update(SET_TEXTS, target)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(app.update(SET_TEXTS, "/categoria/kigurumis")).isOne();
    }

    @Test
    void givenABannerWithoutAPhotoOrTexts_whenPublishedDirectly_thenTheSchemaRefusesIt() {
        assertThatThrownBy(() -> app.update("update home_banner set published = true"))
            .isInstanceOf(DataIntegrityViolationException.class);
        app.update(SET_TEXTS, "/");
        assertThatThrownBy(() -> app.update("update home_banner set published = true"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> app.update("update home_banner set title = null"))
            .as("texts go together").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> app.update("update home_banner set image_storage_key = 'abc'"))
            .as("the photo's columns go together").isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Each call in its own committed transaction, as a use case would run it. */
    record InTransaction(JdbcHomeBannerStore adapter, TransactionTemplate transactions)
        implements LoadHomeBanner, SaveHomeBanner {

        @Override
        public HomeBanner load() {
            return transactions.execute(status -> adapter.load());
        }

        @Override
        public HomeBanner lock() {
            return transactions.execute(status -> adapter.lock());
        }

        @Override
        public void save(HomeBanner banner) {
            transactions.executeWithoutResult(status -> adapter.save(banner));
        }
    }
}
