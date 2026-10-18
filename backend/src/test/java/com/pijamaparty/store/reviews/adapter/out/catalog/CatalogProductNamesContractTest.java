package com.pijamaparty.store.reviews.adapter.out.catalog;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.reviews.application.ProductNamesContractTest;
import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Through the catalog's public API to its tables in PostgreSQL. */
@ApiTest
class CatalogProductNamesContractTest extends ProductNamesContractTest<CatalogProductNames> {

    private final CatalogProductNames adapter;
    private final JdbcTemplate owner;

    CatalogProductNamesContractTest(CatalogProductNames adapter, PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected CatalogProductNames createSubject() {
        return adapter;
    }

    @Override
    protected void givenProduct(ProductId product, String name, boolean published) {
        CatalogRows.product(owner, product.value(), name, published);
        CatalogRows.variant(owner, product.value(), UUID.randomUUID(), true);
    }

    @AfterEach
    void cleanUp() {
        CatalogRows.cleanUp(owner);
    }
}
