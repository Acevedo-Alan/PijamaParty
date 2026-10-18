package com.pijamaparty.store.reviews.adapter.out.catalog;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.reviews.application.ProductLinksContractTest;
import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Through the catalog's public API to its tables in PostgreSQL. */
@ApiTest
class CatalogProductLinksContractTest extends ProductLinksContractTest<CatalogProductLinks> {

    private final CatalogProductLinks adapter;
    private final JdbcTemplate owner;

    CatalogProductLinksContractTest(CatalogProductLinks adapter, PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected CatalogProductLinks createSubject() {
        return adapter;
    }

    @Override
    protected void givenProduct(ProductId product, String name, String slug, boolean published) {
        CatalogRows.product(owner, product.value(), name, slug, published);
        CatalogRows.variant(owner, product.value(), UUID.randomUUID(), true);
    }

    @AfterEach
    void cleanUp() {
        CatalogRows.cleanUp(owner);
    }
}
