package com.pijamaparty.store.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.api.CategoryRenamed;
import com.pijamaparty.store.catalog.api.ProductChanged;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link CatalogEvents}: each call publishes one event of {@code catalog.api} with the id. */
public abstract class CatalogEventsContractTest<T extends CatalogEvents> extends ContractTest<T> {

    /** What reached the listeners, in order. */
    protected abstract List<Object> published();

    @Test
    void givenAChangedProduct_whenPublished_thenListenersGetItsId() {
        UUID id = UUID.randomUUID();

        subject.productChanged(ProductId.of(id));

        assertThat(published()).containsExactly(new ProductChanged(id));
    }

    @Test
    void givenARenamedCategory_whenPublished_thenListenersGetItsId() {
        UUID id = UUID.randomUUID();

        subject.categoryRenamed(CategoryId.of(id));

        assertThat(published()).containsExactly(new CategoryRenamed(id));
    }
}
