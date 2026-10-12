package com.pijamaparty.store.shipping.adapter.out.persistence;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.shipping.application.LoadShippingSettings;
import com.pijamaparty.store.shipping.application.LoadShippingZoneRates;
import com.pijamaparty.store.shipping.application.SaveShippingSettings;
import com.pijamaparty.store.shipping.application.SaveShippingZoneRates;
import com.pijamaparty.store.shipping.domain.Province;
import com.pijamaparty.store.shipping.domain.ShippingSettings;
import com.pijamaparty.store.shipping.domain.ShippingZoneRates;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The shipping costs and settings in tables {@code shipping_zone_rate} and {@code shop_setting} (V16), in the
 * caller's transaction. The database also enforces the ranges of UC-12, so a value the domain let through by mistake
 * fails instead of being stored.
 */
@Repository
class JdbcShippingStore implements LoadShippingZoneRates, SaveShippingZoneRates, LoadShippingSettings,
    SaveShippingSettings {

    private static final String SELECT_RATES = """
        SELECT province, cost_cents
        FROM shipping_zone_rate
        """;

    // Self-conflicting but compatible with readers: two replacements run one after the other, so neither keeps a
    // province the other one removed, while the checkout keeps reading the costs (T-E3-03).
    private static final String LOCK_RATES = """
        LOCK TABLE shipping_zone_rate IN SHARE ROW EXCLUSIVE MODE
        """;

    private static final String DELETE_ALL_RATES = """
        DELETE FROM shipping_zone_rate
        """;

    private static final String DELETE_OTHER_RATES = """
        DELETE FROM shipping_zone_rate
        WHERE province NOT IN (:provinces)
        """;

    // An unchanged cost keeps its updated_at.
    private static final String UPSERT_RATE = """
        INSERT INTO shipping_zone_rate (province, cost_cents, updated_at)
        VALUES (:province, :costCents, :at)
        ON CONFLICT (province) DO UPDATE
        SET cost_cents = EXCLUDED.cost_cents,
            updated_at = EXCLUDED.updated_at
        WHERE shipping_zone_rate.cost_cents <> EXCLUDED.cost_cents
        """;

    private static final String SELECT_SETTINGS = """
        SELECT free_shipping_threshold_cents
        FROM shop_setting
        """;

    private static final String UPDATE_SETTINGS = """
        UPDATE shop_setting
        SET free_shipping_threshold_cents = :thresholdCents,
            updated_at = :at
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcShippingStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ShippingZoneRates loadRates() {
        Map<Province, Money> costs = new EnumMap<>(Province.class);
        jdbc.query(SELECT_RATES, row -> {
            costs.put(Province.valueOf(row.getString("province")), Money.of(row.getLong("cost_cents")));
        });
        return ShippingZoneRates.of(costs);
    }

    @Override
    public void replaceRates(ShippingZoneRates rates, Instant at) {
        jdbc.getJdbcTemplate().execute(LOCK_RATES);
        List<String> provinces = rates.costs().keySet().stream().map(Province::name).toList();
        if (provinces.isEmpty()) {
            jdbc.update(DELETE_ALL_RATES, new MapSqlParameterSource());
            return;
        }
        jdbc.update(DELETE_OTHER_RATES, new MapSqlParameterSource("provinces", provinces));
        MapSqlParameterSource[] upserts = rates.costs().entrySet().stream()
            .map(rate -> new MapSqlParameterSource()
                .addValue("province", rate.getKey().name())
                .addValue("costCents", rate.getValue().cents())
                .addValue("at", at.atOffset(ZoneOffset.UTC)))
            .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(UPSERT_RATE, upserts);
    }

    @Override
    public ShippingSettings loadSettings() {
        Long thresholdCents = jdbc.queryForObject(SELECT_SETTINGS, new MapSqlParameterSource(), Long.class);
        return new ShippingSettings(Optional.ofNullable(thresholdCents).map(Money::of));
    }

    @Override
    public void saveSettings(ShippingSettings settings, Instant at) {
        int updated = jdbc.update(UPDATE_SETTINGS, new MapSqlParameterSource()
            .addValue("thresholdCents", settings.freeShippingThreshold().map(Money::cents).orElse(null), Types.BIGINT)
            .addValue("at", at.atOffset(ZoneOffset.UTC)));
        if (updated != 1) {
            throw new IllegalStateException("shop_setting must have exactly one row, V16 creates it");
        }
    }
}
