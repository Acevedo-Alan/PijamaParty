package com.pijamaparty.store.catalog.adapter.out.persistence;

import com.pijamaparty.store.catalog.application.LoadHomeBanner;
import com.pijamaparty.store.catalog.application.SaveHomeBanner;
import com.pijamaparty.store.catalog.domain.ContentHash;
import com.pijamaparty.store.catalog.domain.HomeBanner;
import com.pijamaparty.store.catalog.domain.HomeBannerPhoto;
import com.pijamaparty.store.catalog.domain.ImagePlaceholder;
import com.pijamaparty.store.catalog.domain.StorageKey;
import com.pijamaparty.store.catalog.domain.StorePath;
import com.pijamaparty.store.catalog.domain.UploadKey;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The banner in the one row of table {@code home_banner} (V39), within the caller's transaction. The migration creates
 * the row, so it is only read and updated.
 */
@Repository
class JdbcHomeBannerStore implements LoadHomeBanner, SaveHomeBanner {

    private static final String SELECT = """
        SELECT title, body, link_text, link_path, image_storage_key, image_placeholder, image_upload_key,
               image_content_sha256, published, updated_at
        FROM home_banner
        WHERE id = 1
        """;

    private static final String SELECT_FOR_UPDATE = """
        SELECT title, body, link_text, link_path, image_storage_key, image_placeholder, image_upload_key,
               image_content_sha256, published, updated_at
        FROM home_banner
        WHERE id = 1
        FOR UPDATE
        """;

    private static final String UPDATE = """
        UPDATE home_banner
        SET title = :title,
            body = :body,
            link_text = :linkText,
            link_path = :linkPath,
            image_storage_key = :storageKey,
            image_placeholder = :placeholder,
            image_upload_key = :uploadKey,
            image_content_sha256 = :contentSha256,
            published = :published,
            updated_at = :updatedAt
        WHERE id = 1
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcHomeBannerStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public HomeBanner load() {
        return jdbc.query(SELECT, EmptySqlParameterSource.INSTANCE, JdbcHomeBannerStore::toBanner).stream()
            .findFirst().orElseThrow(JdbcHomeBannerStore::missingRow);
    }

    @Override
    public HomeBanner lock() {
        return jdbc.query(SELECT_FOR_UPDATE, EmptySqlParameterSource.INSTANCE, JdbcHomeBannerStore::toBanner).stream()
            .findFirst().orElseThrow(JdbcHomeBannerStore::missingRow);
    }

    @Override
    public void save(HomeBanner banner) {
        Optional<HomeBanner.Texts> texts = banner.texts();
        Optional<HomeBannerPhoto> photo = banner.photo();
        var parameters = new MapSqlParameterSource()
            .addValue("title", texts.map(HomeBanner.Texts::title).orElse(null))
            .addValue("body", texts.map(HomeBanner.Texts::body).orElse(null))
            .addValue("linkText", texts.map(HomeBanner.Texts::linkText).orElse(null))
            .addValue("linkPath", texts.map(text -> text.linkPath().value()).orElse(null))
            .addValue("storageKey", photo.map(image -> image.storageKey().value()).orElse(null))
            .addValue("placeholder", photo.map(image -> image.placeholder().value()).orElse(null))
            .addValue("uploadKey", photo.map(image -> image.uploadKey().value()).orElse(null))
            .addValue("contentSha256", photo.map(image -> image.contentHash().digest()).orElse(null))
            .addValue("published", banner.published())
            .addValue("updatedAt", banner.updatedAt().atOffset(ZoneOffset.UTC));
        if (jdbc.update(UPDATE, parameters) != 1) {
            throw missingRow();
        }
    }

    private static IllegalStateException missingRow() {
        return new IllegalStateException("V39 creates the home banner row");
    }

    private static HomeBanner toBanner(ResultSet row, int rowNumber) throws SQLException {
        String title = row.getString("title");
        Optional<HomeBanner.Texts> texts = title == null ? Optional.empty() : Optional.of(new HomeBanner.Texts(
            title, row.getString("body"), row.getString("link_text"), new StorePath(row.getString("link_path"))));
        String storageKey = row.getString("image_storage_key");
        Optional<HomeBannerPhoto> photo = storageKey == null ? Optional.empty() : Optional.of(new HomeBannerPhoto(
            new StorageKey(storageKey),
            new ImagePlaceholder(row.getString("image_placeholder")),
            UploadKey.of(row.getObject("image_upload_key", UUID.class)),
            ContentHash.ofDigest(row.getBytes("image_content_sha256"))));
        return HomeBanner.restore(texts, photo, row.getBoolean("published"),
            row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
