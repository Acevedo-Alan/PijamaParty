package com.pijamaparty.store.catalog.adapter.out.persistence;

import com.pijamaparty.store.catalog.application.StockOverviewFilter;
import com.pijamaparty.store.catalog.application.StockOverviewPages;
import com.pijamaparty.store.catalog.application.StockOverviewRow;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.ProductSearch;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.persistence.LikePattern;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * UC-07 (T-E8-09): the catalog tables joined to the inventory's {@code v_variant_availability} (V43: available, on
 * hand and reserved; ARCHITECTURE.md section 3, rule 4); a variant without a stock row has none. Constant SQL with
 * named parameters (RNF-28).
 */
@Repository
class JdbcStockOverviewPages implements StockOverviewPages {

    // One constant text whose optional conditions each switch off with a null parameter, never built by
    // concatenation. The filter comes from the StockFilter enum; the pattern is the search with %, _ and \ escaped
    // (LikePattern), folded like the name; the SKU is the row's own, compared whole. The variant id breaks ties.
    private static final String PAGE = """
        SELECT v.id, v.product_id, p.name, v.size, v.color, v.sku, p.active,
               coalesce(a.on_hand, 0) AS on_hand, coalesce(a.reserved, 0) AS reserved,
               coalesce(a.available, 0) AS available
        FROM variant v
        JOIN product p ON p.id = v.product_id
        LEFT JOIN v_variant_availability a ON a.variant_id = v.id
        WHERE v.active
          AND (CAST(:filter AS text) = 'ALL'
               OR (CAST(:filter AS text) = 'LOW' AND coalesce(a.available, 0) > 0
                   AND coalesce(a.available, 0) <= CAST(:threshold AS integer))
               OR (CAST(:filter AS text) = 'OUT' AND coalesce(a.available, 0) <= 0))
          AND (CAST(:pattern AS text) IS NULL
               OR lower(unaccent(p.name)) LIKE lower(unaccent(CAST(:pattern AS text))) ESCAPE '\\'
               OR lower(v.sku) = lower(CAST(:sku AS text)))
          AND (CAST(:categoryId AS uuid) IS NULL OR p.category_id = CAST(:categoryId AS uuid))
        ORDER BY available, p.name, v.size_position, v.color_position, v.id
        LIMIT :limit OFFSET :offset
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcStockOverviewPages(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<StockOverviewRow> page(StockOverviewFilter filter, int offset, int limit) {
        var params = new MapSqlParameterSource("offset", offset).addValue("limit", limit)
            .addValue("filter", filter.stock().name(), Types.VARCHAR)
            .addValue("threshold", filter.threshold(), Types.INTEGER)
            .addValue("pattern", filter.search().map(search -> LikePattern.contains(search.text())).orElse(null),
                Types.VARCHAR)
            .addValue("sku", filter.search().map(ProductSearch::text).orElse(null), Types.VARCHAR)
            .addValue("categoryId", filter.category().map(CategoryId::value).orElse(null), Types.OTHER);
        return jdbc.query(PAGE, params, JdbcStockOverviewPages::toRow);
    }

    private static StockOverviewRow toRow(ResultSet row, int rowNumber) throws SQLException {
        return new StockOverviewRow(
            VariantId.of(row.getObject("id", UUID.class)),
            ProductId.of(row.getObject("product_id", UUID.class)),
            row.getString("name"),
            row.getString("size"),
            row.getString("color"),
            row.getString("sku"),
            row.getBoolean("active"),
            row.getInt("on_hand"),
            row.getInt("reserved"),
            row.getInt("available"));
    }
}
