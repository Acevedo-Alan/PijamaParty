package com.pijamaparty.store.reviews.adapter.out.catalog;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.reviews.application.ListedProductsContractTest;
import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Through the catalog's public API to its tables in PostgreSQL: a listed product has a variant visitors can buy. */
@ApiTest
class CatalogListedProductsContractTest extends ListedProductsContractTest<CatalogListedProducts> {

    private final CatalogListedProducts adapter;
    private final JdbcTemplate owner;

    CatalogListedProductsContractTest(CatalogListedProducts adapter, PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected CatalogListedProducts createSubject() {
        return adapter;
    }

    @Override
    protected void givenProduct(ProductId product, String slug, boolean listed) {
        CatalogRows.product(owner, product.value(), "Pijama Luna", slug, listed);
        CatalogRows.variant(owner, product.value(), UUID.randomUUID(), true);
    }

    @AfterEach
    void cleanUp() {
        CatalogRows.cleanUp(owner);
    }
}
