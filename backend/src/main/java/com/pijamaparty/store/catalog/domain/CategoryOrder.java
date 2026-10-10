package com.pijamaparty.store.catalog.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** UC-05: the administrator reorders the categories by sending all of them in the new order. */
public final class CategoryOrder {

    private CategoryOrder() {
    }

    /**
     * Gives every category its index in {@code order} as its position. The order must name every category exactly
     * once; otherwise nothing changes.
     */
    public static Outcome apply(List<Category> categories, List<CategoryId> order, Instant now) {
        Map<CategoryId, Category> byId = categories.stream()
            .collect(Collectors.toMap(Category::id, Function.identity()));
        if (order.size() != byId.size() || !byId.keySet().equals(new HashSet<>(order))) {
            return new Outcome.InvalidOrder();
        }
        List<Category> changed = new ArrayList<>();
        for (int position = 0; position < order.size(); position++) {
            Category category = byId.get(order.get(position));
            if (category.moveTo(position, now)) {
                changed.add(category);
            }
        }
        return new Outcome.Reordered(List.copyOf(changed));
    }

    public sealed interface Outcome {

        /** {@code changed} holds only the categories whose position moved, to be saved. */
        record Reordered(List<Category> changed) implements Outcome {}

        record InvalidOrder() implements Outcome {}
    }
}
