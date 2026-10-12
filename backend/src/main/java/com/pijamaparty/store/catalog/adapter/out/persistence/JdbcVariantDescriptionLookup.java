package com.pijamaparty.store.catalog.adapter.out.persistence;

import com.pijamaparty.store.catalog.api.VariantDescription;
import com.pijamaparty.store.catalog.application.VariantDescriptionLookup;
import com.pijamaparty.store.catalog.domain.ProductImage;
import com.pijamaparty.store.catalog.domain.StorageKey;
import com.pijamaparty.store.catalog.domain.VariantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The description of variants over {@code variant}, {@code product} and {@code product_image}, in the caller's
 * transaction.
 */
@Repository
class JdbcVariantDescriptionLookup implements VariantDescriptionLookup {

    // Every variant, sold or not. Its photo is the first of its color, or else the first of the whole product.
    private static final String SELECT_DESCRIPTIONS = """
        SELECT v.id, p.name, p.slug, v.size, v.color, i.storage_key, i.placeholder
        FROM variant v
        JOIN product p ON p.id = v.product_id
        LEFT JOIN LATERAL (
            SELECT storage_key, placeholder
            FROM product_image
            WHERE product_id = p.id AND (color = v.color OR color IS NULL)
            ORDER BY color IS NULL, position, id
            LIMIT 1
        ) i ON true
        WHERE v.id = ANY (CAST(:variantIds AS uuid[]))
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcVariantDescriptionLookup(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<VariantId, VariantDescription> describe(Collection<VariantId> variants) {
        UUID[] ids = variants.stream().map(VariantId::value).distinct().toArray(UUID[]::new);
        return jdbc.query(SELECT_DESCRIPTIONS, new MapSqlParameterSource().addValue("variantIds", ids, Types.ARRAY),
                (row, n) -> Map.entry(VariantId.of(row.getObject("id", UUID.class)), toDescription(row)))
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static VariantDescription toDescription(ResultSet row) throws SQLException {
        String storageKey = row.getString("storage_key");
        Optional<VariantDescription.Photo> photo = storageKey == null
            ? Optional.empty()
            : Optional.of(photo(new StorageKey(storageKey), row.getString("placeholder")));
        return new VariantDescription(row.getString("name"), row.getString("slug"), row.getString("size"),
            row.getString("color"), photo);
    }

    private static VariantDescription.Photo photo(StorageKey storageKey, String placeholder) {
        return new VariantDescription.Photo(placeholder, ProductImage.WIDTHS.stream()
            .map(width -> new VariantDescription.PhotoFile(width, storageKey.fileName(width)))
            .toList());
    }
}
