package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ProductId;
import java.util.Objects;

/** UC-26: keeps the search index in step with the texts of products and categories. */
public interface RefreshSearchVector {

    void execute(Command command);

    sealed interface Command {

        record OfProduct(ProductId product) implements Command {

            public OfProduct {
                Objects.requireNonNull(product, "product");
            }
        }

        record OfCategory(CategoryId category) implements Command {

            public OfCategory {
                Objects.requireNonNull(category, "category");
            }
        }
    }
}
