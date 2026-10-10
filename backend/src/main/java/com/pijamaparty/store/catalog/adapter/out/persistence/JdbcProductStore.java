package com.pijamaparty.store.catalog.adapter.out.persistence;

import com.pijamaparty.store.catalog.application.LoadProducts;
import com.pijamaparty.store.catalog.application.ProductSummaries;
import com.pijamaparty.store.catalog.application.ProductSummaryView;
import com.pijamaparty.store.catalog.application.SaveProduct;
import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Color;
import com.pijamaparty.store.catalog.domain.Description;
import com.pijamaparty.store.catalog.domain.Material;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductDetails;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.ProductName;
import com.pijamaparty.store.catalog.domain.Season;
import com.pijamaparty.store.catalog.domain.Size;
import com.pijamaparty.store.catalog.domain.Sku;
import com.pijamaparty.store.catalog.domain.Slug;
import com.pijamaparty.store.catalog.domain.Variant;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.domain.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Products in table {@code product} and their variants in {@code variant} (V3), within the caller's transaction. A
 * product and its variants are written together inside a savepoint: a used slug or SKU, or a missing category, rolls
 * back only that write and becomes an outcome.
 */
@Repository
class JdbcProductStore implements LoadProducts, SaveProduct, ProductSummaries {

    private static final String SELECT_BY_ID = """
        SELECT id, category_id, name, slug, description, material, season, audience, active, created_at, updated_at
        FROM product
        WHERE id = :id
        """;

    private static final String LOCK_BY_ID = """
        SELECT id, category_id, name, slug, description, material, season, audience, active, created_at, updated_at
        FROM product
        WHERE id = :id
        FOR UPDATE
        """;

    private static final String LOCK_BY_VARIANT_ID = """
        SELECT p.id, p.category_id, p.name, p.slug, p.description, p.material, p.season, p.audience, p.active,
               p.created_at, p.updated_at
        FROM product p
        JOIN variant v ON v.product_id = p.id
        WHERE v.id = :variantId
        FOR UPDATE OF p
        """;

    private static final String SELECT_VARIANTS = """
        SELECT id, sku, size, size_position, color, color_position, price_cents, active, created_at, updated_at
        FROM variant
        WHERE product_id = :productId
        ORDER BY size_position, color_position, id
        """;

    private static final String SELECT_SUMMARIES = """
        SELECT p.id, p.category_id, p.name, p.slug, p.active,
               (SELECT count(*) FROM variant v WHERE v.product_id = p.id) AS variant_count
        FROM product p
        ORDER BY p.created_at DESC, p.id DESC
        LIMIT :limit OFFSET :offset
        """;

    private static final String INSERT_PRODUCT = """
        INSERT INTO product (id, category_id, name, slug, description, material, season, audience, active,
                             created_at, updated_at)
        VALUES (:id, :categoryId, :name, :slug, :description, :material, :season, :audience, :active,
                :createdAt, :updatedAt)
        """;

    private static final String UPDATE_PRODUCT = """
        UPDATE product
        SET category_id = :categoryId,
            name = :name,
            slug = :slug,
            description = :description,
            material = :material,
            season = :season,
            audience = :audience,
            active = :active,
            updated_at = :updatedAt
        WHERE id = :id
        """;

    // SKU, size and color never change, so a stored variant only gets its positions, price and state.
    private static final String UPSERT_VARIANT = """
        INSERT INTO variant (id, product_id, sku, size, size_position, color, color_position, price_cents, active,
                             created_at, updated_at)
        VALUES (:id, :productId, :sku, :size, :sizePosition, :color, :colorPosition, :priceCents, :active,
                :createdAt, :updatedAt)
        ON CONFLICT (id) DO UPDATE
        SET size_position = EXCLUDED.size_position,
            color_position = EXCLUDED.color_position,
            price_cents = EXCLUDED.price_cents,
            active = EXCLUDED.active,
            updated_at = EXCLUDED.updated_at
        WHERE variant.product_id = EXCLUDED.product_id
        """;

    private static final String SLUG_USED_ELSEWHERE = """
        SELECT EXISTS (SELECT 1 FROM product WHERE slug = :slug AND id <> :id)
        """;

    // A stored variant other than the product's own ones that has one of their SKUs.
    private static final String SKUS_USED_ELSEWHERE = """
        SELECT EXISTS (SELECT 1 FROM variant WHERE sku IN (:skus) AND id NOT IN (:ids))
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Savepoints savepoints;

    JdbcProductStore(NamedParameterJdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.savepoints = new Savepoints(dataSource);
    }

    @Override
    public Optional<Product> findById(ProductId id) {
        return load(SELECT_BY_ID, new MapSqlParameterSource("id", id.value()));
    }

    @Override
    public Optional<Product> lockById(ProductId id) {
        return load(LOCK_BY_ID, new MapSqlParameterSource("id", id.value()));
    }

    @Override
    public Optional<Product> lockByVariantId(VariantId id) {
        return load(LOCK_BY_VARIANT_ID, new MapSqlParameterSource("variantId", id.value()));
    }

    private Optional<Product> load(String sql, SqlParameterSource parameters) {
        return jdbc.query(sql, parameters, JdbcProductStore::toProductRow).stream().findFirst()
            .map(row -> row.toProduct(jdbc.query(SELECT_VARIANTS,
                new MapSqlParameterSource("productId", row.id().value()), JdbcProductStore::toVariant)));
    }

    @Override
    public List<ProductSummaryView> list(int offset, int limit) {
        return jdbc.query(SELECT_SUMMARIES, new MapSqlParameterSource().addValue("offset", offset)
            .addValue("limit", limit), JdbcProductStore::toSummary);
    }

    @Override
    public SaveProduct.Outcome add(Product product) {
        return save(product, INSERT_PRODUCT);
    }

    @Override
    public SaveProduct.Outcome update(Product product) {
        return save(product, UPDATE_PRODUCT);
    }

    private SaveProduct.Outcome save(Product product, String productSql) {
        return savepoints.run(() -> {
            if (jdbc.update(productSql, parametersOf(product)) != 1) {
                throw new IllegalStateException("products are never deleted");
            }
            SqlParameterSource[] variants = product.variants().stream()
                .map(variant -> parametersOf(product.id(), variant))
                .toArray(SqlParameterSource[]::new);
            // A variant id of another product is skipped by the upsert's WHERE: never report it as saved.
            if (Arrays.stream(jdbc.batchUpdate(UPSERT_VARIANT, variants)).anyMatch(rows -> rows != 1)) {
                throw new IllegalStateException("a variant id belongs to another product");
            }
            return SaveProduct.Outcome.SAVED;
        }, violation -> outcomeOf(violation, product));
    }

    /** Runs after the savepoint was rolled back, so the queries see only what other transactions committed. */
    private Optional<SaveProduct.Outcome> outcomeOf(DataIntegrityViolationException violation, Product product) {
        if (Savepoints.isForeignKeyViolation(violation)) {
            // The only foreign key a product row has is product_category_fk; variant_product_fk points to this row.
            return Optional.of(SaveProduct.Outcome.UNKNOWN_CATEGORY);
        }
        if (!(violation instanceof DuplicateKeyException)) {
            return Optional.empty();
        }
        Boolean slugUsed = jdbc.queryForObject(SLUG_USED_ELSEWHERE, new MapSqlParameterSource()
            .addValue("slug", product.slug().value()).addValue("id", product.id().value()), Boolean.class);
        if (Boolean.TRUE.equals(slugUsed)) {
            return Optional.of(SaveProduct.Outcome.SLUG_ALREADY_USED);
        }
        List<String> skus = product.variants().stream().map(variant -> variant.sku().value()).toList();
        List<UUID> ids = product.variants().stream().map(variant -> variant.id().value()).toList();
        boolean skuUsed = !skus.isEmpty()
            && (skus.stream().distinct().count() != skus.size()
                || Boolean.TRUE.equals(jdbc.queryForObject(SKUS_USED_ELSEWHERE,
                    new MapSqlParameterSource().addValue("skus", skus).addValue("ids", ids), Boolean.class)));
        return skuUsed ? Optional.of(SaveProduct.Outcome.SKU_ALREADY_USED) : Optional.empty();
    }

    private static MapSqlParameterSource parametersOf(Product product) {
        ProductDetails details = product.details();
        return new MapSqlParameterSource()
            .addValue("id", product.id().value())
            .addValue("categoryId", details.categoryId().value())
            .addValue("name", details.name().value())
            .addValue("slug", product.slug().value())
            .addValue("description", details.description().value())
            .addValue("material", details.material().value())
            .addValue("season", details.season().name())
            .addValue("audience", details.audience().name())
            .addValue("active", product.published())
            .addValue("createdAt", toTimestamp(product.createdAt()))
            .addValue("updatedAt", toTimestamp(product.updatedAt()));
    }

    private static MapSqlParameterSource parametersOf(ProductId productId, Variant variant) {
        return new MapSqlParameterSource()
            .addValue("id", variant.id().value())
            .addValue("productId", productId.value())
            .addValue("sku", variant.sku().value())
            .addValue("size", variant.size().value())
            .addValue("sizePosition", variant.sizePosition())
            .addValue("color", variant.color().value())
            .addValue("colorPosition", variant.colorPosition())
            .addValue("priceCents", variant.price().map(Money::cents).orElse(null), Types.BIGINT)
            .addValue("active", variant.active())
            .addValue("createdAt", toTimestamp(variant.createdAt()))
            .addValue("updatedAt", toTimestamp(variant.updatedAt()));
    }

    private static OffsetDateTime toTimestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant toInstant(ResultSet row, String column) throws SQLException {
        return row.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static ProductRow toProductRow(ResultSet row, int rowNumber) throws SQLException {
        return new ProductRow(
            ProductId.of(row.getObject("id", UUID.class)),
            new ProductDetails(
                CategoryId.of(row.getObject("category_id", UUID.class)),
                new ProductName(row.getString("name")),
                new Description(row.getString("description")),
                new Material(row.getString("material")),
                Season.valueOf(row.getString("season")),
                Audience.valueOf(row.getString("audience"))),
            new Slug(row.getString("slug")),
            row.getBoolean("active"),
            toInstant(row, "created_at"),
            toInstant(row, "updated_at"));
    }

    private static Variant toVariant(ResultSet row, int rowNumber) throws SQLException {
        long priceCents = row.getLong("price_cents");
        Optional<Money> price = row.wasNull() ? Optional.empty() : Optional.of(Money.of(priceCents));
        return Variant.restore(
            VariantId.of(row.getObject("id", UUID.class)),
            new Sku(row.getString("sku")),
            new Size(row.getString("size")),
            row.getInt("size_position"),
            new Color(row.getString("color")),
            row.getInt("color_position"),
            price,
            row.getBoolean("active"),
            toInstant(row, "created_at"),
            toInstant(row, "updated_at"));
    }

    private static ProductSummaryView toSummary(ResultSet row, int rowNumber) throws SQLException {
        return new ProductSummaryView(
            ProductId.of(row.getObject("id", UUID.class)),
            CategoryId.of(row.getObject("category_id", UUID.class)),
            row.getString("name"),
            row.getString("slug"),
            row.getBoolean("active"),
            row.getInt("variant_count"));
    }

    private record ProductRow(ProductId id, ProductDetails details, Slug slug, boolean published, Instant createdAt,
                              Instant updatedAt) {

        Product toProduct(List<Variant> variants) {
            return Product.restore(id, details, slug, published, createdAt, updatedAt, variants);
        }
    }
}
