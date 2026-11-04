package com.pijamaparty.store.settings.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import com.pijamaparty.store.settings.application.LoadStoreSettings;
import com.pijamaparty.store.settings.application.SaveStoreSettings;
import com.pijamaparty.store.settings.application.StoreSettingsStoreContractTest;
import com.pijamaparty.store.settings.domain.SocialLink;
import com.pijamaparty.store.settings.domain.StoreSettings;
import com.pijamaparty.store.settings.domain.WhatsAppNumber;
import java.time.Instant;
import java.time.OffsetDateTime;
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
class JdbcStoreSettingsStoreContractTest extends StoreSettingsStoreContractTest<JdbcStoreSettingsStoreContractTest.InTransaction> {

    private final InTransaction inTransaction;
    private final JdbcTemplate owner;

    JdbcStoreSettingsStoreContractTest(JdbcStoreSettingsStore adapter, PlatformTransactionManager transactionManager,
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
        StoreSettingsRow.reset(owner);
    }

    @Test
    void givenTheMigration_whenTheSettingsAreLoaded_thenTheyHaveTheBusinessWhatsAppAndInstagramAndTheDefaultThreshold() {
        StoreSettings settings = inTransaction.load();

        assertThat(settings.whatsAppNumber()).map(WhatsAppNumber::digits).contains("5493425304507");
        assertThat(settings.instagram()).map(SocialLink::url).contains("https://www.instagram.com/pijamaparty.zzz/");
        assertThat(settings.email()).isEmpty();
        assertThat(settings.facebook()).isEmpty();
        assertThat(settings.announcement()).isEmpty();
        assertThat(settings.lowStockThreshold().units()).isEqualTo(3);
        assertThat(settings.version()).isZero();
    }

    @Test
    void givenASave_whenItIsWritten_thenTheRowHasItsUpdateTime() {
        Instant at = Instant.parse("2026-11-04T15:30:00Z");
        StoreSettings read = inTransaction.load();

        inTransaction.save(((StoreSettings.Edit.Edited) read.edit(new StoreSettings.Typed(null, null, null, null,
            null, null, null, null, 5L))).settings(), at);

        assertThat(owner.queryForObject("select updated_at from store_settings", OffsetDateTime.class).toInstant())
            .isEqualTo(at);
    }

    /** Each call in its own committed transaction, as a use case would run it. */
    record InTransaction(JdbcStoreSettingsStore adapter, TransactionTemplate transactions)
        implements LoadStoreSettings, SaveStoreSettings {

        @Override
        public StoreSettings load() {
            return transactions.execute(status -> adapter.load());
        }

        @Override
        public boolean save(StoreSettings settings, Instant at) {
            return Boolean.TRUE.equals(transactions.execute(status -> adapter.save(settings, at)));
        }
    }
}
