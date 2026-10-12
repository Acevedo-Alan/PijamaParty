package com.pijamaparty.store.shipping.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.shipping.application.LoadShippingSettings;
import com.pijamaparty.store.shipping.application.LoadShippingZoneRates;
import com.pijamaparty.store.shipping.application.SaveShippingSettings;
import com.pijamaparty.store.shipping.application.SaveShippingZoneRates;
import com.pijamaparty.store.shipping.application.ShippingStoreContractTest;
import com.pijamaparty.store.shipping.domain.Province;
import com.pijamaparty.store.shipping.domain.ShippingSettings;
import com.pijamaparty.store.shipping.domain.ShippingZoneRates;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The adapter runs in its caller's transaction. Here every call of the contract is its own committed transaction, as
 * a use case would run it, connected as the application role.
 */
@ApiTest
class JdbcShippingStoreContractTest extends ShippingStoreContractTest<JdbcShippingStoreContractTest.InTransaction> {

    private static final Instant AT = Instant.parse("2026-10-12T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-13T12:00:00Z");

    private final InTransaction inTransaction;
    private final JdbcTemplate owner;

    JdbcShippingStoreContractTest(JdbcShippingStore adapter, PlatformTransactionManager transactionManager,
                                  PostgreSQLContainer<?> postgres) {
        this.inTransaction = new InTransaction(adapter, new TransactionTemplate(transactionManager));
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected InTransaction createSubject() {
        return inTransaction;
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate shipping_zone_rate");
        owner.update("update shop_setting set free_shipping_threshold_cents = null");
    }

    @Test
    void givenStoredCosts_whenReplaced_thenOnlyTheChangedOnesGetANewUpdateTime() {
        inTransaction.replaceRates(ShippingZoneRates.of(Map.of(Province.SANTA_FE, Money.of(300_000),
            Province.CORDOBA, Money.of(500_000))), AT);

        inTransaction.replaceRates(ShippingZoneRates.of(Map.of(Province.SANTA_FE, Money.of(300_000),
            Province.CORDOBA, Money.of(550_000))), LATER);

        assertThat(updatedAt(Province.SANTA_FE)).isEqualTo(AT);
        assertThat(updatedAt(Province.CORDOBA)).isEqualTo(LATER);
    }

    private Instant updatedAt(Province province) {
        return owner.queryForObject("select updated_at from shipping_zone_rate where province = ?",
            OffsetDateTime.class, province.name()).toInstant();
    }

    /** Each call in its own committed transaction, as a use case would run it. */
    record InTransaction(JdbcShippingStore adapter, TransactionTemplate transactions)
        implements LoadShippingZoneRates, SaveShippingZoneRates, LoadShippingSettings, SaveShippingSettings {

        @Override
        public ShippingZoneRates loadRates() {
            return transactions.execute(status -> adapter.loadRates());
        }

        @Override
        public void replaceRates(ShippingZoneRates rates, Instant at) {
            transactions.executeWithoutResult(status -> adapter.replaceRates(rates, at));
        }

        @Override
        public ShippingSettings loadSettings() {
            return transactions.execute(status -> adapter.loadSettings());
        }

        @Override
        public void saveSettings(ShippingSettings settings, Instant at) {
            transactions.executeWithoutResult(status -> adapter.saveSettings(settings, at));
        }
    }
}
