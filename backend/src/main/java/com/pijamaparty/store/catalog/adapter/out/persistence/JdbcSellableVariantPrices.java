package com.pijamaparty.store.catalog.adapter.out.persistence;

import com.pijamaparty.store.catalog.application.SellableVariantPrices;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.platform.domain.Money;
import java.sql.Types;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The prices of sellable variants over {@code variant}, {@code product} and {@code category}, in the caller's
 * transaction.
 */
@Repository
class JdbcSellableVariantPrices implements SellableVariantPrices {

    // Sellable as a variant of a listed product (ListedProductsSql.LIST): published, of an active category, the variant
    // active and priced.
    private static final String SELECT_PRICES = """
        SELECT v.id, v.price_cents
        FROM variant v
        JOIN product p ON p.id = v.product_id AND p.active
        JOIN category c ON c.id = p.category_id AND c.active
        WHERE v.id = ANY (CAST(:variantIds AS uuid[]))
          AND v.active
          AND v.price_cents IS NOT NULL
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcSellableVariantPrices(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<VariantId, Money> pricesOf(Collection<VariantId> variants) {
        UUID[] ids = variants.stream().map(VariantId::value).distinct().toArray(UUID[]::new);
        return jdbc.query(SELECT_PRICES, new MapSqlParameterSource().addValue("variantIds", ids, Types.ARRAY),
                (row, n) -> Map.entry(VariantId.of(row.getObject("id", UUID.class)),
                    Money.of(row.getLong("price_cents"))))
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
