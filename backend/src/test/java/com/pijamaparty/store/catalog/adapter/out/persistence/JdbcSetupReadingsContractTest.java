package com.pijamaparty.store.catalog.adapter.out.persistence;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.catalog.api.CatalogSetup.Named;
import com.pijamaparty.store.catalog.application.SetupReadings;
import com.pijamaparty.store.catalog.application.SetupReadingsContractTest;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Runs on committed rows stored as the owner, each read in its own transaction (UC-46). */
@ApiTest
class JdbcSetupReadingsContractTest extends SetupReadingsContractTest<SetupReadings> {

    /** The banner as V39 creates it. */
    private static final String RESET_BANNER = """
        update home_banner
        set title = null, body = null, link_text = null, link_path = null, image_storage_key = null,
            image_placeholder = null, image_upload_key = null, image_content_sha256 = null, published = false
        """;

    private static final String PUBLISH_BANNER = """
        update home_banner
        set title = 'Personajes', body = '', link_text = 'Ver', link_path = '/', image_storage_key = 'banner',
            image_placeholder = 'data:image/webp;base64,AAAA', image_upload_key = ?, image_content_sha256 = ?,
            published = true
        """;

    private final InTransaction inTransaction;
    private final JdbcTemplate owner;

    JdbcSetupReadingsContractTest(JdbcSetupReadings adapter, PlatformTransactionManager transactionManager,
                                  PostgreSQLContainer<?> postgres) {
        this.inTransaction = new InTransaction(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected SetupReadings createSubject() {
        return inTransaction;
    }

    @Override
    protected UUID givenCategory(String name, int position, boolean active, boolean sizeGuide) {
        UUID category = UUID.randomUUID();
        owner.update("""
            insert into category (id, name, slug, position, active, created_at, updated_at)
            values (?, ?, ?, ?, ?, now(), now())
            """, category, name, "categoria-" + category, position, active);
        if (sizeGuide) {
            owner.update("""
                insert into size_guide (category_id, column_names, sizes, cells, note, updated_at)
                values (?, '{Busto}'::text[], '{M}'::text[], '{{90}}'::text[], null, now())
                """, category);
        }
        return category;
    }

    @Override
    protected UUID givenProduct(UUID category, String name, boolean published, int minutesAgo, boolean photo) {
        UUID product = UUID.randomUUID();
        owner.update("""
            insert into product (id, category_id, name, slug, description, material, season, audience, active,
                                 created_at, updated_at)
            values (?, ?, ?, ?, '', 'Algodón', 'SUMMER', 'WOMEN', ?, now() - make_interval(mins => ?), now())
            """, product, category, name, "producto-" + product, published, minutesAgo);
        if (photo) {
            owner.update("""
                insert into product_image (id, product_id, storage_key, placeholder, position, upload_key,
                                           content_sha256, created_at)
                values (?, ?, ?, 'data:image/webp;base64,AAAA', 0, ?, ?, now())
                """, UUID.randomUUID(), product, "foto-" + product, UUID.randomUUID(), new byte[32]);
        }
        return product;
    }

    @Override
    protected void givenBanner(boolean published) {
        if (published) {
            owner.update(PUBLISH_BANNER, UUID.randomUUID(), new byte[32]);
        } else {
            owner.update(RESET_BANNER);
        }
    }

    @BeforeEach
    @AfterEach
    void cleanUp() {
        owner.execute("truncate stock_movement, stock_level, product_image, variant, product, size_guide, category");
        owner.update(RESET_BANNER);
    }

    /** Each read in its own transaction, as a use case would run it. */
    record InTransaction(JdbcSetupReadings adapter, TransactionTemplate transactions) implements SetupReadings {

        private <R> R in(Supplier<R> work) {
            return transactions.execute(status -> work.get());
        }

        @Override
        public Named categoriesWithoutSizeGuide(int limit) {
            return in(() -> adapter.categoriesWithoutSizeGuide(limit));
        }

        @Override
        public Named publishedProductsWithoutPhoto(int limit) {
            return in(() -> adapter.publishedProductsWithoutPhoto(limit));
        }

        @Override
        public boolean homeBannerPublished() {
            return Boolean.TRUE.equals(in(adapter::homeBannerPublished));
        }
    }
}
