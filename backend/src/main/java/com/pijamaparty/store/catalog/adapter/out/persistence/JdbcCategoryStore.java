package com.pijamaparty.store.catalog.adapter.out.persistence;

import com.pijamaparty.store.catalog.application.LoadCategories;
import com.pijamaparty.store.catalog.application.RemoveCategory;
import com.pijamaparty.store.catalog.application.SaveCategory;
import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.CategoryName;
import com.pijamaparty.store.catalog.domain.Slug;
import com.pijamaparty.store.platform.persistence.Savepoints;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import javax.sql.DataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Categories in table {@code category} (V3), within the caller's transaction. The database enforces the unique slug
 * and keeps categories with products (foreign key {@code product_category_fk}); each write that may break one of them
 * runs inside a savepoint, so the violation becomes an outcome and the caller's transaction stays usable.
 */
@Repository
class JdbcCategoryStore implements LoadCategories, SaveCategory, RemoveCategory {

    private static final String SELECT_BY_ID = """
        SELECT id, name, slug, position, active, created_at, updated_at
        FROM category
        WHERE id = :id
        """;

    private static final String SELECT_ALL = """
        SELECT id, name, slug, position, active, created_at, updated_at
        FROM category
        ORDER BY position, created_at, id
        """;

    private static final String INSERT = """
        INSERT INTO category (id, name, slug, position, active, created_at, updated_at)
        VALUES (:id, :name, :slug, :position, :active, :createdAt, :updatedAt)
        """;

    // A plain UPDATE: a category deleted since it was read is not brought back.
    private static final String UPDATE = """
        UPDATE category
        SET name = :name,
            slug = :slug,
            position = :position,
            active = :active,
            updated_at = :updatedAt
        WHERE id = :id
        """;

    private static final String DELETE = """
        DELETE FROM category
        WHERE id = :id
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Savepoints savepoints;

    JdbcCategoryStore(NamedParameterJdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.savepoints = new Savepoints(dataSource);
    }

    @Override
    public Optional<Category> findById(CategoryId id) {
        return jdbc.query(SELECT_BY_ID, new MapSqlParameterSource("id", id.value()), JdbcCategoryStore::toCategory)
            .stream().findFirst();
    }

    @Override
    public List<Category> findAll() {
        return jdbc.query(SELECT_ALL, JdbcCategoryStore::toCategory);
    }

    @Override
    public SaveCategory.Outcome add(Category category) {
        // Ids are new UUIDs, so the only unique constraint a new row can break is the slug's.
        return updateUnlessViolating(() -> jdbc.update(INSERT, parametersOf(category)),
            DuplicateKeyException.class::isInstance)
            .isPresent() ? SaveCategory.Outcome.SAVED : SaveCategory.Outcome.SLUG_ALREADY_USED;
    }

    @Override
    public SaveCategory.Outcome update(Category category) {
        // The id does not change, so the only unique constraint an update can break is the slug's.
        OptionalInt updated = updateUnlessViolating(() -> jdbc.update(UPDATE, parametersOf(category)),
            DuplicateKeyException.class::isInstance);
        if (updated.isEmpty()) {
            return SaveCategory.Outcome.SLUG_ALREADY_USED;
        }
        return updated.getAsInt() == 0 ? SaveCategory.Outcome.NOT_FOUND : SaveCategory.Outcome.SAVED;
    }

    private static MapSqlParameterSource parametersOf(Category category) {
        return new MapSqlParameterSource()
            .addValue("id", category.id().value())
            .addValue("name", category.name().value())
            .addValue("slug", category.slug().value())
            .addValue("position", category.position())
            .addValue("active", category.active())
            .addValue("createdAt", toTimestamp(category.createdAt()))
            .addValue("updatedAt", toTimestamp(category.updatedAt()));
    }

    @Override
    public RemoveCategory.Outcome remove(CategoryId id) {
        // The only foreign key that points to category is product_category_fk.
        OptionalInt deleted = updateUnlessViolating(
            () -> jdbc.update(DELETE, new MapSqlParameterSource("id", id.value())),
            Savepoints::isForeignKeyViolation);
        if (deleted.isEmpty()) {
            return RemoveCategory.Outcome.HAS_PRODUCTS;
        }
        return deleted.getAsInt() == 0 ? RemoveCategory.Outcome.NOT_FOUND : RemoveCategory.Outcome.REMOVED;
    }

    /**
     * Runs the statement inside a savepoint of the caller's transaction. Returns the affected rows, or empty when it
     * broke an {@code expected} constraint: then only the statement is rolled back. Any other failure propagates.
     */
    private OptionalInt updateUnlessViolating(IntSupplier statement,
                                              Predicate<DataIntegrityViolationException> expected) {
        return savepoints.run(() -> OptionalInt.of(statement.getAsInt()),
            violation -> expected.test(violation) ? Optional.of(OptionalInt.empty()) : Optional.empty());
    }

    private static OffsetDateTime toTimestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Category toCategory(ResultSet row, int rowNumber) throws SQLException {
        return Category.restore(
            CategoryId.of(row.getObject("id", UUID.class)),
            new CategoryName(row.getString("name")),
            new Slug(row.getString("slug")),
            row.getInt("position"),
            row.getBoolean("active"),
            row.getObject("created_at", OffsetDateTime.class).toInstant(),
            row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
