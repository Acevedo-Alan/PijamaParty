package com.pijamaparty.store.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.catalog.application.LoadSizeGuides;
import com.pijamaparty.store.catalog.application.RemoveSizeGuide;
import com.pijamaparty.store.catalog.application.SaveSizeGuide;
import com.pijamaparty.store.catalog.application.SizeGuideStoreContractTest;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Size;
import com.pijamaparty.store.catalog.domain.SizeGuide;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Every call of the contract is its own committed transaction, as a use case would run it, written by the application
 * role. The schema tests below check that V38 backs the domain's rules on its own.
 */
@ApiTest
class JdbcSizeGuideStoreContractTest extends SizeGuideStoreContractTest<JdbcSizeGuideStoreContractTest.InTransaction> {

    private static final String INSERT_GUIDE = """
        insert into size_guide (category_id, column_names, sizes, cells, note, updated_at)
        values (?, ?::text[], ?::text[], ?::text[], ?, now())
        """;

    private final InTransaction inTransaction;
    private final JdbcTemplate app;
    private final JdbcTemplate owner;

    JdbcSizeGuideStoreContractTest(JdbcSizeGuideStore adapter, PlatformTransactionManager transactionManager,
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

    @Override
    protected CategoryId givenACategory() {
        var id = UUID.randomUUID();
        app.update("""
            insert into category (id, name, slug, position, active, created_at, updated_at)
            values (?, ?, ?, 0, true, now(), now())
            """, id, "Categoría " + id, "categoria-" + id);
        return CategoryId.of(id);
    }

    @Override
    protected void deleteCategory(CategoryId category) {
        app.update("delete from category where id = ?", category.value());
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate size_guide, product_image, variant, product, category");
    }

    @Test
    void givenARowWithoutAValueForEachColumn_whenStoredDirectly_thenTheSchemaRefusesIt() {
        UUID category = givenACategory().value();

        assertThatThrownBy(() -> app.update(INSERT_GUIDE, category, "{Busto,Cintura}", "{S,M}", "{{1,2},{3}}", null))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> app.update(INSERT_GUIDE, category, "{Busto,Cintura}", "{S,M}", "{{1,2,3},{4,5,6}}",
            null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> app.update(INSERT_GUIDE, category, "{Busto}", "{S,M}", "{{1}}", null))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void givenTextsOutOfTheirLimits_whenStoredDirectly_thenTheSchemaRefusesThem() {
        UUID category = givenACategory().value();

        assertThatThrownBy(() -> app.update(INSERT_GUIDE, category, "{\" Busto\"}", "{S}", "{{1}}", null))
            .as("untrimmed column").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> app.update(INSERT_GUIDE, category, "{Busto}", "{\"\"}", "{{1}}", null))
            .as("empty size").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> app.update(INSERT_GUIDE, category, "{Busto}", "{S}", "{{" + "9".repeat(21) + "}}",
            null)).as("long value").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> app.update(INSERT_GUIDE, category, "{a,b,c,d,e,f,g}", "{S}", "{{1,2,3,4,5,6,7}}",
            null)).as("seven columns").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> app.update(INSERT_GUIDE, category, "{Busto}", "{S}", "{{1}}", ""))
            .as("empty note").isInstanceOf(DataIntegrityViolationException.class);

        assertThat(app.update(INSERT_GUIDE, category, "{Busto}", "{S}", "{{\"\"}}", null)).as("empty value").isOne();
    }

    @Test
    void givenTextsWithQuotesCommasAndBraces_whenSavedAndLoaded_thenTheyComeBackUnchanged() {
        CategoryId category = givenACategory();
        var tricky = new SizeGuide(category, List.of("Largo \"total\"", "a,b"),
            List.of(new SizeGuide.Row(new Size("{XL}"),
                List.of("NULL", "\\ 1"))), Optional.empty(), Instant.parse("2026-10-18T12:00:00Z"));

        inTransaction.save(tricky);

        assertThat(inTransaction.findByCategory(category)).contains(tricky);
    }

    /** Each call in its own committed transaction, as a use case would run it. */
    record InTransaction(JdbcSizeGuideStore adapter, TransactionTemplate transactions)
        implements LoadSizeGuides, SaveSizeGuide, RemoveSizeGuide {

        @Override
        public Optional<SizeGuide> findByCategory(CategoryId categoryId) {
            return transactions.execute(status -> adapter.findByCategory(categoryId));
        }

        @Override
        public SaveSizeGuide.Outcome save(SizeGuide guide) {
            return transactions.execute(status -> adapter.save(guide));
        }

        @Override
        public void remove(CategoryId categoryId) {
            transactions.executeWithoutResult(status -> adapter.remove(categoryId));
        }
    }
}
