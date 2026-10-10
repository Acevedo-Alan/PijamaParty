package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.VariantMatrix;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.List;

/**
 * UC-06: the administrator gives sizes and colors and the system creates the missing size × color variants, active
 * and without a price, each with its stock level at 0. Repeating a generation creates nothing.
 */
public interface GenerateVariantMatrix {

    Result execute(Command command);

    /** Sizes and colors as typed, in display order: the use case trims and validates them. */
    record Command(Actor actor, ProductId productId, List<String> sizes, List<String> colors) {

        public Command {
            sizes = List.copyOf(sizes);
            colors = List.copyOf(colors);
        }
    }

    sealed interface Result {

        /** {@code created} counts the new variants; 0 when the product had every combination. */
        record Generated(ProductView product, int created) implements Result {}

        record NotFound() implements Result {}

        record InvalidMatrix(VariantMatrix.Problem problem) implements Result {}

        record Forbidden() implements Result {}
    }
}
