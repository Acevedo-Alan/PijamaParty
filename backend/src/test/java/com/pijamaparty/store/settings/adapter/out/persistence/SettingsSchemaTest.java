package com.pijamaparty.store.settings.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.testing.ApiTest;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** T-E8-04: the settings schema (V42) backs the rules of UC-36, connected as the application role. */
@ApiTest
class SettingsSchemaTest {

    private final JdbcTemplate app;
    private final JdbcTemplate owner;

    SettingsSchemaTest(JdbcTemplate jdbc, PostgreSQLContainer<?> postgres) {
        this.app = jdbc;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        StoreSettingsRow.reset(owner);
    }

    @Test
    void givenTheMigration_whenTheSettingsAreRead_thenThereIsASingleRow() {
        assertThat(app.queryForObject("select count(*) from store_settings", Integer.class)).isOne();
    }

    @Test
    void givenValidValues_whenStored_thenTheyAreAccepted() {
        assertThat(app.update("""
            update store_settings
            set email = 'hola@pijamaparty.com.ar', whatsapp_number = '5493424000000',
                opening_hours = 'Lunes a viernes de 9 a 18 h', address = 'San Martín 2000, Santa Fe',
                instagram_url = 'https://instagram.com/pijamaparty', facebook_url = 'https://m.facebook.com/pijamaparty',
                tiktok_url = 'https://www.tiktok.com/@pijamaparty', announcement = 'Envío gratis a todo Santa Fe',
                low_stock_threshold = 1000
            """)).isOne();
    }

    @Test
    void givenAValueThatBreaksItsRule_whenStored_thenItIsRejected() {
        rejected("store_settings_email_ck", () -> app.update("update store_settings set email = 'Hola@pijama.com'"));
        rejected("store_settings_email_ck", () -> app.update("update store_settings set email = 'sin arroba'"));
        rejected("store_settings_whatsapp_number_ck",
            () -> app.update("update store_settings set whatsapp_number = '+5493424000000'"));
        rejected("store_settings_whatsapp_number_ck",
            () -> app.update("update store_settings set whatsapp_number = '123456789'"));
        rejected("store_settings_opening_hours_ck",
            () -> app.update("update store_settings set opening_hours = repeat('a', 81)"));
        rejected("store_settings_opening_hours_ck",
            () -> app.update("update store_settings set opening_hours = ' 9 a 18 h'"));
        rejected("store_settings_address_ck", () -> app.update("update store_settings set address = repeat('a', 121)"));
        rejected("store_settings_address_ck", () -> app.update("update store_settings set address = ''"));
        rejected("store_settings_instagram_url_ck",
            () -> app.update("update store_settings set instagram_url = 'http://instagram.com/x'"));
        rejected("store_settings_instagram_url_ck",
            () -> app.update("update store_settings set instagram_url = 'https://instagram.com.evil.example/x'"));
        rejected("store_settings_instagram_url_ck",
            () -> app.update("update store_settings set instagram_url = 'https://evil.example/instagram.com'"));
        rejected("store_settings_instagram_url_ck",
            () -> app.update("update store_settings set instagram_url = 'https://instagram.com/'"));
        rejected("store_settings_facebook_url_ck",
            () -> app.update("update store_settings set facebook_url = 'https://instagram.com/x'"));
        rejected("store_settings_tiktok_url_ck",
            () -> app.update("update store_settings set tiktok_url = 'https://tiktok.com:8443/x'"));
        rejected("store_settings_announcement_ck",
            () -> app.update("update store_settings set announcement = E'Envío gratis\na todo el país'"));
        rejected("store_settings_announcement_ck",
            () -> app.update("update store_settings set announcement = repeat('a', 81)"));
        rejected("store_settings_low_stock_threshold_ck",
            () -> app.update("update store_settings set low_stock_threshold = -1"));
        rejected("store_settings_low_stock_threshold_ck",
            () -> app.update("update store_settings set low_stock_threshold = 1001"));
        rejected("store_settings_version_ck", () -> app.update("update store_settings set version = -1"));
    }

    private static void rejected(String constraint, ThrowingCallable update) {
        assertThatThrownBy(update).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(constraint);
    }

    @Test
    void givenTheSettings_whenTheApplicationTriesToAddOrDeleteARow_thenItIsDenied() {
        assertThatThrownBy(() -> app.update("""
            insert into store_settings (id, low_stock_threshold, version, updated_at) values (false, 3, 0, now())
            """)).isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
        assertThatThrownBy(() -> app.update("delete from store_settings"))
            .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
    }
}
