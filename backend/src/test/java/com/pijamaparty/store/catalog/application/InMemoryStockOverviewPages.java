package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductSearch;
import com.pijamaparty.store.catalog.domain.Variant;
import com.pijamaparty.store.catalog.domain.VariantId;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fake of {@link StockOverviewPages} over the products of an {@link InMemoryProductStore}, with the inventory's units
 * on hand and reserved set by the test; a variant never set has none. Ids break ties as PostgreSQL does.
 */
public class InMemoryStockOverviewPages implements StockOverviewPages {

    /** The keys of the SQL's ORDER BY: available, product name, size and color position, then the variant id. */
    private static final Comparator<Listed> ORDER = Comparator.comparingInt((Listed listed) -> listed.row().available())
        .thenComparing(listed -> listed.row().productName())
        .thenComparingInt(listed -> listed.variant().sizePosition())
        .thenComparingInt(listed -> listed.variant().colorPosition())
        .thenComparing(listed -> listed.variant().id().value().toString());

    /** A row with the variant it comes from, for its display order. */
    private record Listed(StockOverviewRow row, Variant variant) {}

    private final InMemoryProductStore products;
    private final Map<VariantId, Integer> onHand = new ConcurrentHashMap<>();
    private final Map<VariantId, Integer> reserved = new ConcurrentHashMap<>();

    public InMemoryStockOverviewPages(InMemoryProductStore products) {
        this.products = products;
    }

    public void setOnHand(VariantId variant, int units) {
        onHand.put(variant, units);
    }

    public void setReserved(VariantId variant, int units) {
        reserved.put(variant, units);
    }

    @Override
    public List<StockOverviewRow> page(StockOverviewFilter filter, int offset, int limit) {
        return products.all().stream()
            .filter(product -> filter.category().map(product.details().categoryId()::equals).orElse(true))
            .flatMap(product -> product.variants().stream().filter(Variant::active)
                .filter(variant -> filter.search().map(search -> matches(product, variant, search)).orElse(true))
                .map(variant -> new Listed(row(product, variant), variant)))
            .filter(listed -> switch (filter.stock()) {
                case ALL -> true;
                case LOW -> listed.row().available() > 0 && listed.row().available() <= filter.threshold();
                case OUT -> listed.row().available() <= 0;
            })
            .sorted(ORDER)
            .skip(offset)
            .limit(limit)
            .map(Listed::row)
            .toList();
    }

    private StockOverviewRow row(Product product, Variant variant) {
        int units = onHand.getOrDefault(variant.id(), 0);
        int held = reserved.getOrDefault(variant.id(), 0);
        return new StockOverviewRow(variant.id(), product.id(), product.details().name().value(),
            variant.size().value(), variant.color().value(), variant.sku().value(), product.published(), units, held,
            units - held);
    }

    private static boolean matches(Product product, Variant variant, ProductSearch search) {
        return folded(product.details().name().value()).contains(folded(search.text()))
            || variant.sku().value().equalsIgnoreCase(search.text());
    }

    /** As {@code lower(unaccent(...))}. */
    private static String folded(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
