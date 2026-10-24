package com.pijamaparty.store.catalog.adapter.out.persistence;

import com.pijamaparty.store.catalog.application.ListedProductSlugs;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** The slugs of the listed products over {@code product}, {@code category} and {@code variant}. */
@Repository
class JdbcListedProductSlugs implements ListedProductSlugs {

    // Listed as in ListedProductsSql.LIST: published, of an active category, with an active priced variant; in its
    // default order (newest first).
    private static final String SELECT_SLUGS = """
        SELECT p.slug
        FROM product p
        JOIN category c ON c.id = p.category_id AND c.active
        WHERE p.active
          AND EXISTS (SELECT 1 FROM variant v WHERE v.product_id = p.id AND v.active AND v.price_cents IS NOT NULL)
        ORDER BY p.created_at DESC, p.id DESC
        LIMIT :limit
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcListedProductSlugs(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> newestFirst(int limit) {
        return jdbc.queryForList(SELECT_SLUGS, new MapSqlParameterSource("limit", limit), String.class);
    }
}
