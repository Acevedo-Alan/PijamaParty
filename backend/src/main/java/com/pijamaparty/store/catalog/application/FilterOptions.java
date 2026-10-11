package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.Season;
import com.pijamaparty.store.platform.domain.Money;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the filter panel offers (UC-02), from the listed products: sizes and colors of active priced variants with
 * available stock, by smallest display position and then text; materials by text; seasons and audiences in enum
 * order; and the lowest and highest "from" price, empty without listed products.
 */
public record FilterOptions(List<String> sizes, List<String> colors, List<String> materials, List<Season> seasons,
                            List<Audience> audiences, Optional<Money> minPrice, Optional<Money> maxPrice) {

    public FilterOptions {
        sizes = List.copyOf(sizes);
        colors = List.copyOf(colors);
        materials = List.copyOf(materials);
        seasons = List.copyOf(seasons);
        audiences = List.copyOf(audiences);
        Objects.requireNonNull(minPrice, "minPrice");
        Objects.requireNonNull(maxPrice, "maxPrice");
    }
}
