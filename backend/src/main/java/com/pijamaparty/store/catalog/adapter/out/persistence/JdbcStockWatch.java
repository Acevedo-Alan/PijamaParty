package com.pijamaparty.store.catalog.adapter.out.persistence;

import com.pijamaparty.store.catalog.api.LowStockVariants.LowStockVariant;
import com.pijamaparty.store.catalog.application.StockWatch;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * UC-33: the catalog tables joined to the inventory's {@code v_variant_availability} (ARCHITECTURE.md section 3, rule
 * 4); a variant without a stock row has none. Constant SQL with named parameters (RNF-28).
 */
@Repository
class JdbcStockWatch implements StockWatch {

    // Low stock: an active variant of a published product at or under the threshold (LIST and COUNT share the rule).
    private static final String LIST = """
        SELECT v.id, v.product_id, p.name, v.size, v.color, coalesce(a.available, 0) AS available
        FROM variant v
        JOIN product p ON p.id = v.product_id AND p.active
        LEFT JOIN v_variant_availability a ON a.variant_id = v.id
        WHERE v.active AND coalesce(a.available, 0) <= :threshold
        ORDER BY available, p.name, v.size_position, v.color_position, v.id
        LIMIT :limit
        """;

    private static final String COUNT = """
        SELECT count(*)
        FROM variant v
        JOIN product p ON p.id = v.product_id AND p.active
        LEFT JOIN v_variant_availability a ON a.variant_id = v.id
        WHERE v.active AND coalesce(a.available, 0) <= :threshold
        """;

    private static final String PUBLISHED = """
        SELECT count(*) FROM product WHERE active
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcStockWatch(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LowStockVariant> lowStock(int threshold, int limit) {
        return jdbc.query(LIST, new MapSqlParameterSource("threshold", threshold).addValue("limit", limit),
            (row, n) -> new LowStockVariant(row.getObject("id", UUID.class), row.getObject("product_id", UUID.class),
                row.getString("name"), row.getString("size"), row.getString("color"), row.getLong("available")));
    }

    @Override
    public long lowStockCount(int threshold) {
        Long count = jdbc.queryForObject(COUNT, new MapSqlParameterSource("threshold", threshold), Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public long publishedProducts() {
        Long count = jdbc.queryForObject(PUBLISHED, new MapSqlParameterSource(), Long.class);
        return count == null ? 0 : count;
    }
}
