package com.pijamaparty.store.catalog.adapter.out.persistence;

import com.pijamaparty.store.catalog.application.ListedProduct;
import com.pijamaparty.store.catalog.application.ProductSearchQuery;
import com.pijamaparty.store.catalog.application.ProductSuggestion;
import com.pijamaparty.store.catalog.application.SearchVectors;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ProductFilter;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.ProductSort;
import com.pijamaparty.store.catalog.domain.SearchText;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The text search (UC-26) over {@code product.search_vector} (V10), PostgreSQL full text with the spanish
 * configuration and {@code unaccent}, within the caller's transaction. The vector is weighed once, by the SQL
 * function {@code product_search_vector}. Every query is a constant text block with the text bound (RNF-28).
 */
@Repository
class JdbcProductSearch implements ProductSearchQuery, SearchVectors {

    // A listed product (as in ListedProductsSql.LIST) whose vector holds the prefix query.
    private static final String SUGGEST = """
        WITH suggestion AS (
            SELECT to_tsquery('spanish', unaccent(CAST(:prefixQuery AS text))) AS query
        )
        SELECT p.slug, p.name, i.storage_key, i.placeholder
        FROM product p
        JOIN category c ON c.id = p.category_id AND c.active
        LEFT JOIN LATERAL (
            SELECT storage_key, placeholder
            FROM product_image
            WHERE product_id = p.id
            ORDER BY position, id
            LIMIT 1
        ) i ON true
        WHERE p.active
          AND EXISTS (SELECT 1 FROM variant v WHERE v.product_id = p.id AND v.active AND v.price_cents IS NOT NULL)
          AND p.search_vector @@ (SELECT query FROM suggestion)
        ORDER BY ts_rank(p.search_vector, (SELECT query FROM suggestion)) DESC, p.created_at DESC, p.id DESC
        LIMIT :limit
        """;

    private static final String REFRESH_PRODUCT = """
        UPDATE product SET search_vector = product_search_vector(id) WHERE id = :id
        """;

    private static final String REFRESH_CATEGORY = """
        UPDATE product SET search_vector = product_search_vector(id) WHERE category_id = :categoryId
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcProductSearch(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ListedProduct> search(SearchText text, ProductFilter filter, Optional<ProductSort> sort,
                                      int offset, int limit) {
        String order = sort.map(ProductSort::name).orElse(ListedProductsSql.RELEVANCE);
        return jdbc.query(ListedProductsSql.LIST, ListedProductsSql.parameters(text.value(), filter, order, offset,
            limit), ListedProductsSql::toListed);
    }

    @Override
    public long count(SearchText text, ProductFilter filter) {
        return jdbc.queryForObject(ListedProductsSql.COUNT, ListedProductsSql.parameters(text.value(), filter),
            Long.class);
    }

    @Override
    public List<ProductSuggestion> suggest(SearchText text, int limit) {
        List<String> words = text.words();
        if (words.isEmpty()) {
            return List.of();
        }
        var parameters = new MapSqlParameterSource()
            .addValue("prefixQuery", prefixQuery(words))
            .addValue("limit", limit);
        return jdbc.query(SUGGEST, parameters, JdbcProductSearch::toSuggestion);
    }

    @Override
    public void refreshProduct(ProductId product) {
        jdbc.update(REFRESH_PRODUCT, new MapSqlParameterSource("id", product.value()));
    }

    @Override
    public void refreshCategory(CategoryId category) {
        jdbc.update(REFRESH_CATEGORY, new MapSqlParameterSource("categoryId", category.value()));
    }

    /**
     * {@code w1 & w2 & last:*} in {@code to_tsquery} syntax. The words hold only letters and digits, so the visitor
     * cannot add operators; the query is still bound as a parameter.
     */
    static String prefixQuery(List<String> words) {
        return String.join(" & ", words) + ":*";
    }

    private static ProductSuggestion toSuggestion(ResultSet row, int rowNumber) throws SQLException {
        return new ProductSuggestion(row.getString("slug"), row.getString("name"), ListedProductsSql.image(row));
    }
}
