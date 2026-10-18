package com.pijamaparty.store.reviews.adapter.out.catalog;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.reviews.application.ProductVariantsContractTest;
import com.pijamaparty.store.reviews.domain.ProductId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Through the catalog's public API to its tables in PostgreSQL. */
@ApiTest
class CatalogProductVariantsContractTest extends ProductVariantsContractTest<CatalogProductVariants> {

    private final CatalogProductVariants adapter;
    private final JdbcTemplate owner;

    CatalogProductVariantsContractTest(CatalogProductVariants adapter, PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected CatalogProductVariants createSubject() {
        return adapter;
    }

    @Override
    protected void givenProduct(ProductId product, boolean published, UUID soldVariant, UUID unsoldVariant) {
        CatalogRows.product(owner, product.value(), "Pijama Luna", published);
        CatalogRows.variant(owner, product.value(), soldVariant, true);
        CatalogRows.variant(owner, product.value(), unsoldVariant, false);
    }

    @AfterEach
    void cleanUp() {
        CatalogRows.cleanUp(owner);
    }
}
