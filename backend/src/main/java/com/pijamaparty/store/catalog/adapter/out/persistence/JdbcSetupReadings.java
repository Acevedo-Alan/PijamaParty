package com.pijamaparty.store.catalog.adapter.out.persistence;

import com.pijamaparty.store.catalog.api.CatalogSetup.Named;
import com.pijamaparty.store.catalog.api.CatalogSetup.Thing;
import com.pijamaparty.store.catalog.application.SetupReadings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * UC-46: the gaps of the catalog the setup guide names, over the catalog tables in the caller's transaction. Constant
 * SQL with named parameters (RNF-28); each list and its count share their rule.
 */
@Repository
class JdbcSetupReadings implements SetupReadings {

    // An active category with a published product and no size guide.
    private static final String CATEGORIES_WHERE = """
        FROM category c
        WHERE c.active
          AND EXISTS (SELECT 1 FROM product p WHERE p.category_id = c.id AND p.active)
          AND NOT EXISTS (SELECT 1 FROM size_guide g WHERE g.category_id = c.id)
        """;

    private static final String CATEGORIES = "SELECT c.id, c.name " + CATEGORIES_WHERE
        + "ORDER BY c.position, c.name, c.id LIMIT :limit";

    private static final String CATEGORIES_COUNT = "SELECT count(*) " + CATEGORIES_WHERE;

    // A published product without any photo.
    private static final String PRODUCTS_WHERE = """
        FROM product p
        WHERE p.active
          AND NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id = p.id)
        """;

    private static final String PRODUCTS = "SELECT p.id, p.name " + PRODUCTS_WHERE
        + "ORDER BY p.created_at DESC, p.id LIMIT :limit";

    private static final String PRODUCTS_COUNT = "SELECT count(*) " + PRODUCTS_WHERE;

    private static final String BANNER = "SELECT coalesce(bool_or(published), false) FROM home_banner";

    private final NamedParameterJdbcTemplate jdbc;

    JdbcSetupReadings(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Each constant query runs at its call site (the SQL gate flags a query passed through a parameter).
    @Override
    public Named categoriesWithoutSizeGuide(int limit) {
        List<Thing> first = jdbc.query(CATEGORIES, new MapSqlParameterSource("limit", limit), JdbcSetupReadings::thing);
        return named(first, jdbc.queryForObject(CATEGORIES_COUNT, new MapSqlParameterSource(), Long.class));
    }

    @Override
    public Named publishedProductsWithoutPhoto(int limit) {
        List<Thing> first = jdbc.query(PRODUCTS, new MapSqlParameterSource("limit", limit), JdbcSetupReadings::thing);
        return named(first, jdbc.queryForObject(PRODUCTS_COUNT, new MapSqlParameterSource(), Long.class));
    }

    @Override
    public boolean homeBannerPublished() {
        return Boolean.TRUE.equals(jdbc.queryForObject(BANNER, new MapSqlParameterSource(), Boolean.class));
    }

    private static Thing thing(ResultSet row, int rowNumber) throws SQLException {
        return new Thing(row.getObject("id", UUID.class), row.getString("name"));
    }

    private static Named named(List<Thing> first, Long total) {
        return new Named(first, total == null ? first.size() : total);
    }
}
