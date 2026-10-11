package com.pijamaparty.store.catalog.adapter.out.persistence;

import com.pijamaparty.store.catalog.application.FilterOptions;
import com.pijamaparty.store.catalog.application.FilterOptionsQuery;
import com.pijamaparty.store.catalog.application.ProductListingItem;
import com.pijamaparty.store.catalog.application.ProductListingQuery;
import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Color;
import com.pijamaparty.store.catalog.domain.ImagePlaceholder;
import com.pijamaparty.store.catalog.domain.Material;
import com.pijamaparty.store.catalog.domain.ProductFilter;
import com.pijamaparty.store.catalog.domain.ProductSort;
import com.pijamaparty.store.catalog.domain.Season;
import com.pijamaparty.store.catalog.domain.Size;
import com.pijamaparty.store.catalog.domain.StorageKey;
import com.pijamaparty.store.platform.domain.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The public grid and its filter options (UC-01, UC-02) over {@code product}, {@code category}, {@code variant},
 * {@code product_image} and the inventory's view {@code v_variant_availability} (V9), within the caller's transaction.
 *
 * <p>Each query is one constant text block (RNF-28): a filter is switched off by an empty array or a null parameter,
 * and the sort is a {@code CASE} on the bound enum name, so no SQL is built at run time.
 */
@Repository
class JdbcProductListingQuery implements ProductListingQuery, FilterOptionsQuery {

    // A listed product: published, of an active category, with an active priced variant. Its "from" price is the
    // lowest price of those variants, and it is sold out when none of them has available stock.
    private static final String LIST = """
        WITH listed AS (
            SELECT p.id, p.slug, p.name, p.created_at,
                   min(v.price_cents) AS from_price_cents,
                   NOT bool_or(coalesce(a.available, 0) > 0) AS sold_out
            FROM product p
            JOIN category c ON c.id = p.category_id AND c.active
            JOIN variant v ON v.product_id = p.id AND v.active AND v.price_cents IS NOT NULL
            LEFT JOIN v_variant_availability a ON a.variant_id = v.id
            WHERE p.active
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
        ORDER BY CASE WHEN :sort = 'PRICE_ASC' THEN l.from_price_cents END ASC,
                 CASE WHEN :sort = 'PRICE_DESC' THEN l.from_price_cents END DESC,
                 l.created_at DESC, l.id DESC
        LIMIT :limit OFFSET :offset
        """;

    // The same listed products as LIST. Seasons and audiences come back as names and are put in enum order here.
    private static final String OPTIONS = """
        WITH listed AS (
            SELECT p.id, p.material, p.season, p.audience, min(v.price_cents) AS from_price_cents
            FROM product p
            JOIN category c ON c.id = p.category_id AND c.active
            JOIN variant v ON v.product_id = p.id AND v.active AND v.price_cents IS NOT NULL
            WHERE p.active
              AND (CAST(:categoryId AS uuid) IS NULL OR p.category_id = CAST(:categoryId AS uuid))
            GROUP BY p.id
        ),
        in_stock AS (
            SELECT v.size, v.size_position, v.color, v.color_position
            FROM listed l
            JOIN variant v ON v.product_id = l.id AND v.active AND v.price_cents IS NOT NULL
            JOIN v_variant_availability a ON a.variant_id = v.id AND a.available > 0
        )
        SELECT
            ARRAY(SELECT size FROM in_stock GROUP BY size
                  ORDER BY min(size_position), size COLLATE "C") AS sizes,
            ARRAY(SELECT color FROM in_stock GROUP BY color
                  ORDER BY min(color_position), color COLLATE "C") AS colors,
            ARRAY(SELECT DISTINCT material COLLATE "C" FROM listed ORDER BY 1) AS materials,
            ARRAY(SELECT DISTINCT season FROM listed) AS seasons,
            ARRAY(SELECT DISTINCT audience FROM listed) AS audiences,
            (SELECT min(from_price_cents) FROM listed) AS min_price_cents,
            (SELECT max(from_price_cents) FROM listed) AS max_price_cents
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcProductListingQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ProductListingItem> list(ProductFilter filter, ProductSort sort, int offset, int limit) {
        var parameters = new MapSqlParameterSource()
            .addValue("categoryId", filter.category().map(CategoryId::value).orElse(null), Types.OTHER)
            .addValue("sizes", texts(filter.sizes(), Size::value), Types.ARRAY)
            .addValue("colors", texts(filter.colors(), Color::value), Types.ARRAY)
            .addValue("materials", texts(filter.materials(), Material::value), Types.ARRAY)
            .addValue("seasons", texts(filter.seasons(), Season::name), Types.ARRAY)
            .addValue("audiences", texts(filter.audiences(), Audience::name), Types.ARRAY)
            .addValue("minPriceCents", filter.minPrice().map(Money::cents).orElse(null), Types.BIGINT)
            .addValue("maxPriceCents", filter.maxPrice().map(Money::cents).orElse(null), Types.BIGINT)
            .addValue("sort", sort.name())
            .addValue("offset", offset)
            .addValue("limit", limit);
        return jdbc.query(LIST, parameters, JdbcProductListingQuery::toItem);
    }

    @Override
    public FilterOptions options(Optional<CategoryId> category) {
        var parameters = new MapSqlParameterSource("categoryId", category.map(CategoryId::value).orElse(null));
        parameters.registerSqlType("categoryId", Types.OTHER);
        return jdbc.queryForObject(OPTIONS, parameters, JdbcProductListingQuery::toOptions);
    }

    /** A text array (never a list, which Spring would expand into several parameters). */
    private static <T> String[] texts(Collection<T> values, Function<T, String> text) {
        return values.stream().map(text).toArray(String[]::new);
    }

    private static ProductListingItem toItem(ResultSet row, int rowNumber) throws SQLException {
        String storageKey = row.getString("storage_key");
        Optional<ProductListingItem.ListingImage> image = storageKey == null
            ? Optional.empty()
            : Optional.of(ProductListingItem.ListingImage.of(new StorageKey(storageKey),
                new ImagePlaceholder(row.getString("placeholder"))));
        return new ProductListingItem(row.getString("slug"), row.getString("name"), image,
            Money.of(row.getLong("from_price_cents")), row.getBoolean("sold_out"));
    }

    private static FilterOptions toOptions(ResultSet row, int rowNumber) throws SQLException {
        List<String> seasons = strings(row, "seasons");
        List<String> audiences = strings(row, "audiences");
        return new FilterOptions(strings(row, "sizes"), strings(row, "colors"), strings(row, "materials"),
            Arrays.stream(Season.values()).filter(season -> seasons.contains(season.name())).toList(),
            Arrays.stream(Audience.values()).filter(audience -> audiences.contains(audience.name())).toList(),
            price(row, "min_price_cents"), price(row, "max_price_cents"));
    }

    private static List<String> strings(ResultSet row, String column) throws SQLException {
        return List.of((String[]) row.getArray(column).getArray());
    }

    private static Optional<Money> price(ResultSet row, String column) throws SQLException {
        long cents = row.getLong(column);
        return row.wasNull() ? Optional.empty() : Optional.of(Money.of(cents));
    }
}
