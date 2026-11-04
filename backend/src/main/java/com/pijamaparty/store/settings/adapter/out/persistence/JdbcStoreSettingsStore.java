package com.pijamaparty.store.settings.adapter.out.persistence;

import com.pijamaparty.store.settings.application.LoadStoreSettings;
import com.pijamaparty.store.settings.application.SaveStoreSettings;
import com.pijamaparty.store.settings.domain.ContactEmail;
import com.pijamaparty.store.settings.domain.LowStockThreshold;
import com.pijamaparty.store.settings.domain.PlainText;
import com.pijamaparty.store.settings.domain.SocialLink;
import com.pijamaparty.store.settings.domain.StoreSettings;
import com.pijamaparty.store.settings.domain.WhatsAppNumber;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The store's settings in table {@code store_settings} (V42), a single row, in the caller's transaction. The database
 * also enforces the rules of UC-36, so a value the domain let through by mistake fails instead of being stored.
 */
@Repository
class JdbcStoreSettingsStore implements LoadStoreSettings, SaveStoreSettings {

    private static final String SELECT = """
        SELECT email, whatsapp_number, opening_hours, address, instagram_url, facebook_url, tiktok_url, announcement,
               low_stock_threshold, version
        FROM store_settings
        """;

    // Only from the version before: two saves from the same version cannot both win (UC-36).
    private static final String UPDATE = """
        UPDATE store_settings
        SET email = :email,
            whatsapp_number = :whatsAppNumber,
            opening_hours = :openingHours,
            address = :address,
            instagram_url = :instagramUrl,
            facebook_url = :facebookUrl,
            tiktok_url = :tiktokUrl,
            announcement = :announcement,
            low_stock_threshold = :lowStockThreshold,
            version = :version,
            updated_at = :at
        WHERE version = :previousVersion
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcStoreSettingsStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public StoreSettings load() {
        return jdbc.queryForObject(SELECT, new MapSqlParameterSource(), (row, rowNumber) -> new StoreSettings(
            text(row, "email", ContactEmail::new),
            text(row, "whatsapp_number", WhatsAppNumber::new),
            text(row, "opening_hours", PlainText::new),
            text(row, "address", PlainText::new),
            text(row, "instagram_url", url -> new SocialLink(SocialLink.Network.INSTAGRAM, url)),
            text(row, "facebook_url", url -> new SocialLink(SocialLink.Network.FACEBOOK, url)),
            text(row, "tiktok_url", url -> new SocialLink(SocialLink.Network.TIKTOK, url)),
            text(row, "announcement", PlainText::new),
            new LowStockThreshold(row.getInt("low_stock_threshold")),
            row.getLong("version")));
    }

    @Override
    public boolean save(StoreSettings settings, Instant at) {
        int updated = jdbc.update(UPDATE, new MapSqlParameterSource()
            .addValue("email", settings.email().map(ContactEmail::value).orElse(null))
            .addValue("whatsAppNumber", settings.whatsAppNumber().map(WhatsAppNumber::digits).orElse(null))
            .addValue("openingHours", settings.openingHours().map(PlainText::value).orElse(null))
            .addValue("address", settings.address().map(PlainText::value).orElse(null))
            .addValue("instagramUrl", settings.instagram().map(SocialLink::url).orElse(null))
            .addValue("facebookUrl", settings.facebook().map(SocialLink::url).orElse(null))
            .addValue("tiktokUrl", settings.tiktok().map(SocialLink::url).orElse(null))
            .addValue("announcement", settings.announcement().map(PlainText::value).orElse(null))
            .addValue("lowStockThreshold", settings.lowStockThreshold().units())
            .addValue("version", settings.version())
            .addValue("previousVersion", settings.version() - 1)
            .addValue("at", at.atOffset(ZoneOffset.UTC)));
        return updated == 1;
    }

    private static <T> Optional<T> text(ResultSet row, String column, Function<String, T> type) throws SQLException {
        return Optional.ofNullable(row.getString(column)).map(type);
    }
}
