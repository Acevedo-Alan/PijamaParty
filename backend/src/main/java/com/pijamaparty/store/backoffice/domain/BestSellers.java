package com.pijamaparty.store.backoffice.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * UC-33: the products with the most units sold, every size and color of a product together. Ties go to the larger
 * amount, then to the name.
 */
public final class BestSellers {

    private static final Comparator<Sold> RANKING = Comparator.comparingLong(Sold::units).reversed()
        .thenComparing(Comparator.comparing(Sold::amount).reversed())
        .thenComparing(Sold::productName);

    private BestSellers() {
    }

    /** What sold of a variant, or of a product once its variants are added up. */
    public record Sold(String productSlug, String productName, long units, Money amount) {

        public Sold {
            Objects.requireNonNull(productSlug, "productSlug");
            Objects.requireNonNull(productName, "productName");
            Objects.requireNonNull(amount, "amount");
        }

        Sold plus(Sold other) {
            return new Sold(productSlug, productName, units + other.units, amount.plus(other.amount));
        }
    }

    /** The first {@code limit} products of the ranking, from what sold of each variant. */
    public static List<Sold> top(List<Sold> perVariant, int limit) {
        Map<String, Sold> perProduct = new LinkedHashMap<>();
        perVariant.forEach(sold -> perProduct.merge(sold.productSlug(), sold, Sold::plus));
        return perProduct.values().stream().sorted(RANKING).limit(limit).toList();
    }
}
