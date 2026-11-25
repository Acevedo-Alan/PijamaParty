package com.pijamaparty.store.backoffice.adapter.out.settings;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.backoffice.application.StoreContactFactsContractTest;
import com.pijamaparty.store.platform.testing.ApiTest;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Through the settings module's public API to PostgreSQL (UC-36, UC-46). The settings stored as the owner. */
@ApiTest
class SettingsStoreContactFactsContractTest extends StoreContactFactsContractTest<SettingsStoreContactFacts> {

    /** The row as V42 leaves it: the number and the Instagram link the business sent, no email. */
    private static final String RESET = """
        update store_settings
        set whatsapp_number = '5493425304507', email = null,
            instagram_url = 'https://www.instagram.com/pijamaparty.zzz/'
        """;

    private final SettingsStoreContactFacts adapter;
    private final JdbcTemplate owner;

    SettingsStoreContactFactsContractTest(SettingsStoreContactFacts adapter, PostgreSQLContainer<?> postgres) {
        this.adapter = adapter;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @Override
    protected SettingsStoreContactFacts createSubject() {
        return adapter;
    }

    @Override
    protected void givenContact(boolean whatsApp, boolean email, boolean instagram) {
        owner.update("update store_settings set whatsapp_number = ?, email = ?, instagram_url = ?",
            whatsApp ? "5493424000000" : null, email ? "hola@pijamaparty.com.ar" : null,
            instagram ? "https://instagram.com/pijamaparty" : null);
    }

    @AfterEach
    void backToTheSeededRow() {
        owner.update(RESET);
    }
}
