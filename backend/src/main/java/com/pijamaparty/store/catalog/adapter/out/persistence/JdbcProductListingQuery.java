package com.pijamaparty.store.catalog.adapter.out.persistence;

import com.pijamaparty.store.catalog.application.FilterOptions;
import com.pijamaparty.store.catalog.application.FilterOptionsQuery;
import com.pijamaparty.store.catalog.application.ListedProduct;
import com.pijamaparty.store.catalog.application.ProductListingQuery;
import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ProductFilter;
import com.pijamaparty.store.catalog.domain.ProductSort;
import com.pijamaparty.store.catalog.domain.Season;
import com.pijamaparty.store.platform.domain.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The public grid and its filter options (UC-01, UC-02) over {@code product}, {@code category}, {@code variant},
 * {@code product_image} and the inventory's view {@code v_variant_availability} (V9), within the caller's transaction.
 *
 * <p>Each query is one constant text block (RNF-28), the grid's in {@link ListedProductsSql}, so no SQL is built at
 * run time.
 */
@Repository
class JdbcProductListingQuery implements ProductListingQuery, FilterOptionsQuery {

    // The same listed products as ListedProductsSql.LIST, without a text. Seasons and audiences come back as names
    // and are put in enum order here.
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
    public List<ListedProduct> list(ProductFilter filter, ProductSort sort, int offset, int limit) {
        return jdbc.query(ListedProductsSql.LIST, ListedProductsSql.parameters(null, filter, sort.name(), offset, limit),
            ListedProductsSql::toListed);
    }

    @Override
    public long count(ProductFilter filter) {
        return jdbc.queryForObject(ListedProductsSql.COUNT, ListedProductsSql.parameters(null, filter), Long.class);
    }

    @Override
    public FilterOptions options(Optional<CategoryId> category) {
        var parameters = new MapSqlParameterSource("categoryId", category.map(CategoryId::value).orElse(null));
        parameters.registerSqlType("categoryId", Types.OTHER);
        return jdbc.queryForObject(OPTIONS, parameters, JdbcProductListingQuery::toOptions);
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
