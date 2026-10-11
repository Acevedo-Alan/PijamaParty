package com.pijamaparty.store.catalog.adapter.out.persistence;

import com.pijamaparty.store.catalog.application.ProductListingItem;
import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Color;
import com.pijamaparty.store.catalog.domain.ImagePlaceholder;
import com.pijamaparty.store.catalog.domain.Material;
import com.pijamaparty.store.catalog.domain.ProductFilter;
import com.pijamaparty.store.catalog.domain.Season;
import com.pijamaparty.store.catalog.domain.Size;
import com.pijamaparty.store.catalog.domain.StorageKey;
import com.pijamaparty.store.platform.domain.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * The query of the public grid (UC-01, UC-02), shared by the listing and the text search (UC-26), so a filter is
 * written once. It is one constant text block (RNF-28): a filter is switched off by an empty array or a null
 * parameter, a null {@code :q} switches the text off, and the sort is a {@code CASE} on a bound name.
 */
final class ListedProductsSql {

    /** The {@code :sort} of a search without an explicit sort: most relevant first. */
    static final String RELEVANCE = "RELEVANCE";

    // A listed product: published, of an active category, with an active priced variant. Its "from" price is the
    // lowest price of those variants, and it is sold out when none of them has available stock. With a text, it must
    // match its search vector (web search syntax, spanish configuration, accents ignored).
    static final String LIST = """
        WITH search AS (
            SELECT websearch_to_tsquery('spanish', unaccent(CAST(:q AS text))) AS query
        ),
        listed AS (
            SELECT p.id, p.slug, p.name, p.created_at, p.search_vector,
                   min(v.price_cents) AS from_price_cents,
                   NOT bool_or(coalesce(a.available, 0) > 0) AS sold_out
            FROM product p
            JOIN category c ON c.id = p.category_id AND c.active
            JOIN variant v ON v.product_id = p.id AND v.active AND v.price_cents IS NOT NULL
            LEFT JOIN v_variant_availability a ON a.variant_id = v.id
            WHERE p.active
              AND (CAST(:q AS text) IS NULL OR p.search_vector @@ (SELECT query FROM search))
              AND (CAST(:categoryId AS uuid) IS NULL OR p.category_id = CAST(:categoryId AS uuid))
              AND (cardinality(CAST(:materials AS text[])) = 0 OR p.material = ANY (CAST(:materials AS text[])))
              AND (cardinality(CAST(:seasons AS text[])) = 0 OR p.season = ANY (CAST(:seasons AS text[])))
              AND (cardinality(CAST(:audiences AS text[])) = 0 OR p.audience = ANY (CAST(:audiences AS text[])))
            GROUP BY p.id
        )
        SELECT l.slug, l.name, l.from_price_cents, l.sold_out, i.storage_key, i.placeholder
        FROM listed l
        LEFT JOIN LATERAL (
            SELECT storage_key, placeholder
            FROM product_image
            WHERE product_id = l.id
            ORDER BY position, id
            LIMIT 1
        ) i ON true
        WHERE (CAST(:minPriceCents AS bigint) IS NULL OR l.from_price_cents >= CAST(:minPriceCents AS bigint))
          AND (CAST(:maxPriceCents AS bigint) IS NULL OR l.from_price_cents <= CAST(:maxPriceCents AS bigint))
          AND (cardinality(CAST(:sizes AS text[])) = 0 AND cardinality(CAST(:colors AS text[])) = 0
               OR EXISTS (
                   SELECT 1
                   FROM variant v
                   JOIN v_variant_availability a ON a.variant_id = v.id
                   WHERE v.product_id = l.id AND v.active AND v.price_cents IS NOT NULL AND a.available > 0
                     AND (cardinality(CAST(:sizes AS text[])) = 0 OR v.size = ANY (CAST(:sizes AS text[])))
                     AND (cardinality(CAST(:colors AS text[])) = 0 OR v.color = ANY (CAST(:colors AS text[])))))
        ORDER BY CASE WHEN :sort = 'RELEVANCE' THEN ts_rank(l.search_vector, (SELECT query FROM search)) END DESC,
                 CASE WHEN :sort = 'PRICE_ASC' THEN l.from_price_cents END ASC,
                 CASE WHEN :sort = 'PRICE_DESC' THEN l.from_price_cents END DESC,
                 l.created_at DESC, l.id DESC
        LIMIT :limit OFFSET :offset
        """;

    private ListedProductsSql() {
    }

    /** The parameters of {@link #LIST}; {@code text} null lists without a search. */
    static MapSqlParameterSource parameters(String text, ProductFilter filter, String sort, int offset, int limit) {
        return new MapSqlParameterSource()
            .addValue("q", text, Types.VARCHAR)
            .addValue("categoryId", filter.category().map(CategoryId::value).orElse(null), Types.OTHER)
            .addValue("sizes", texts(filter.sizes(), Size::value), Types.ARRAY)
            .addValue("colors", texts(filter.colors(), Color::value), Types.ARRAY)
            .addValue("materials", texts(filter.materials(), Material::value), Types.ARRAY)
            .addValue("seasons", texts(filter.seasons(), Season::name), Types.ARRAY)
            .addValue("audiences", texts(filter.audiences(), Audience::name), Types.ARRAY)
            .addValue("minPriceCents", filter.minPrice().map(Money::cents).orElse(null), Types.BIGINT)
            .addValue("maxPriceCents", filter.maxPrice().map(Money::cents).orElse(null), Types.BIGINT)
            .addValue("sort", sort)
            .addValue("offset", offset)
            .addValue("limit", limit);
    }

    static ProductListingItem toItem(ResultSet row, int rowNumber) throws SQLException {
        return new ProductListingItem(row.getString("slug"), row.getString("name"), image(row),
            Money.of(row.getLong("from_price_cents")), row.getBoolean("sold_out"));
    }

    /** The first photo from the columns {@code storage_key} and {@code placeholder}, if the product has one. */
    static Optional<ProductListingItem.ListingImage> image(ResultSet row) throws SQLException {
        String storageKey = row.getString("storage_key");
        return storageKey == null
            ? Optional.empty()
            : Optional.of(ProductListingItem.ListingImage.of(new StorageKey(storageKey),
                new ImagePlaceholder(row.getString("placeholder"))));
    }

    /** A text array (never a list, which Spring would expand into several parameters). */
    private static <T> String[] texts(Collection<T> values, Function<T, String> text) {
        return values.stream().map(text).toArray(String[]::new);
    }
}
