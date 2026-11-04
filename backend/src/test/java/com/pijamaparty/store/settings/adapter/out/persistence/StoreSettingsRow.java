package com.pijamaparty.store.settings.adapter.out.persistence;

import org.springframework.jdbc.core.JdbcTemplate;

/** Puts the single row of {@code store_settings} back as V42 created it, for the tests that change it. */
public final class StoreSettingsRow {

    private StoreSettingsRow() {
    }

    public static void reset(JdbcTemplate owner) {
        owner.update("""
            update store_settings
            set email = null, whatsapp_number = '5493425304507', opening_hours = null, address = null,
                instagram_url = 'https://www.instagram.com/pijamaparty.zzz/', facebook_url = null, tiktok_url = null,
                announcement = null, low_stock_threshold = 3, version = 0
            """);
    }
}
