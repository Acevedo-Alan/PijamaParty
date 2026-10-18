package com.pijamaparty.store.catalog.adapter.out.persistence;

import com.pijamaparty.store.catalog.application.LoadSizeGuides;
import com.pijamaparty.store.catalog.application.RemoveSizeGuide;
import com.pijamaparty.store.catalog.application.SaveSizeGuide;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Size;
import com.pijamaparty.store.catalog.domain.SizeGuide;
import com.pijamaparty.store.platform.persistence.Savepoints;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.support.AbstractSqlTypeValue;
import org.springframework.stereotype.Repository;

/**
 * Size guides in table {@code size_guide} (V38), within the caller's transaction. The values of the rows go in the
 * two-dimensional {@code cells}, one row per size. A guide of a category deleted meanwhile breaks the foreign key
 * inside a savepoint, so it becomes an outcome and the caller's transaction stays usable.
 */
@Repository
class JdbcSizeGuideStore implements LoadSizeGuides, SaveSizeGuide, RemoveSizeGuide {

    private static final String SELECT = """
        SELECT category_id, column_names, sizes, cells, note, updated_at
        FROM size_guide
        WHERE category_id = :categoryId
        """;

    private static final String UPSERT = """
        INSERT INTO size_guide (category_id, column_names, sizes, cells, note, updated_at)
        VALUES (:categoryId, :columns, :sizes, :cells, :note, :updatedAt)
        ON CONFLICT (category_id) DO UPDATE
        SET column_names = EXCLUDED.column_names,
            sizes = EXCLUDED.sizes,
            cells = EXCLUDED.cells,
            note = EXCLUDED.note,
            updated_at = EXCLUDED.updated_at
        """;

    private static final String DELETE = """
        DELETE FROM size_guide
        WHERE category_id = :categoryId
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Savepoints savepoints;

    JdbcSizeGuideStore(NamedParameterJdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.savepoints = new Savepoints(dataSource);
    }

    @Override
    public Optional<SizeGuide> findByCategory(CategoryId categoryId) {
        return jdbc.query(SELECT, new MapSqlParameterSource("categoryId", categoryId.value()),
            JdbcSizeGuideStore::toGuide).stream().findFirst();
    }

    @Override
    public SaveSizeGuide.Outcome save(SizeGuide guide) {
        String[] sizes = guide.rows().stream().map(row -> row.size().value()).toArray(String[]::new);
        String[][] cells = guide.rows().stream().map(row -> row.values().toArray(String[]::new))
            .toArray(String[][]::new);
        var parameters = new MapSqlParameterSource()
            .addValue("categoryId", guide.categoryId().value())
            .addValue("columns", textArray(guide.columns().toArray(String[]::new)))
            .addValue("sizes", textArray(sizes))
            .addValue("cells", textArray(cells))
            .addValue("note", guide.note().orElse(null))
            .addValue("updatedAt", guide.updatedAt().atOffset(ZoneOffset.UTC));
        // The only foreign key of size_guide is the category's.
        return savepoints.run(() -> {
            jdbc.update(UPSERT, parameters);
            return SaveSizeGuide.Outcome.SAVED;
        }, violation -> Savepoints.isForeignKeyViolation(violation)
            ? Optional.of(SaveSizeGuide.Outcome.CATEGORY_NOT_FOUND) : Optional.empty());
    }

    @Override
    public void remove(CategoryId categoryId) {
        jdbc.update(DELETE, new MapSqlParameterSource("categoryId", categoryId.value()));
    }

    /** A {@code text[]} of any dimension, built by the driver. */
    private static AbstractSqlTypeValue textArray(Object[] elements) {
        return new AbstractSqlTypeValue() {
            @Override
            protected Object createTypeValue(Connection connection, int sqlType, String typeName) throws SQLException {
                return connection.createArrayOf("text", elements);
            }
        };
    }

    private static SizeGuide toGuide(ResultSet row, int rowNumber) throws SQLException {
        String[] sizes = (String[]) row.getArray("sizes").getArray();
        String[][] cells = (String[][]) row.getArray("cells").getArray();
        var rows = new ArrayList<SizeGuide.Row>(sizes.length);
        for (int i = 0; i < sizes.length; i++) {
            rows.add(new SizeGuide.Row(new Size(sizes[i]), List.of(cells[i])));
        }
        return new SizeGuide(
            CategoryId.of(row.getObject("category_id", UUID.class)),
            List.of((String[]) row.getArray("column_names").getArray()),
            rows,
            Optional.ofNullable(row.getString("note")),
            row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
