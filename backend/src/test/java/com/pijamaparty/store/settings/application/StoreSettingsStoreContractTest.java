package com.pijamaparty.store.settings.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.settings.domain.StoreSettings;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Contract of {@link LoadStoreSettings} and {@link SaveStoreSettings} over the same storage (UC-36). */
public abstract class StoreSettingsStoreContractTest<T extends LoadStoreSettings & SaveStoreSettings>
    extends ContractTest<T> {

    private static final Instant AT = Instant.parse("2026-11-04T12:00:00Z");

    /** Every field with a value, one version after {@code current}. */
    private static StoreSettings everything(StoreSettings current) {
        return ((StoreSettings.Edit.Edited) current.edit(new StoreSettings.Typed("hola@pijamaparty.com.ar",
            "5493424000000", "Lunes a viernes de 9 a 18 h", "San Martín 2000, Santa Fe",
            "https://www.instagram.com/pijamaparty.zzz/", "https://www.facebook.com/pijamaparty",
            "https://www.tiktok.com/@pijamaparty", "Envío gratis a todo Santa Fe", 7L))).settings();
    }

    /** Every field empty, one version after {@code current}. */
    private static StoreSettings nothing(StoreSettings current) {
        return ((StoreSettings.Edit.Edited) current.edit(new StoreSettings.Typed(null, null, null, null, null, null,
            null, null, 0L))).settings();
    }

    @Test
    void givenSettingsWithEveryValue_whenSavedFromTheStoredVersion_thenTheyAreLoadedBack() {
        StoreSettings saved = everything(subject.load());

        assertThat(subject.save(saved, AT)).isTrue();
        persistAndForget();

        assertThat(subject.load()).isEqualTo(saved);
    }

    @Test
    void givenSettingsWithValues_whenSavedEmpty_thenEveryValueIsGone() {
        subject.save(everything(subject.load()), AT);
        StoreSettings empty = nothing(subject.load());

        assertThat(subject.save(empty, AT)).isTrue();
        persistAndForget();

        assertThat(subject.load()).isEqualTo(empty);
    }

    @Test
    void givenTwoSavesFromTheSameVersion_whenBothAreWritten_thenOnlyTheFirstIsKept() {
        StoreSettings read = subject.load();
        StoreSettings first = everything(read);
        StoreSettings second = nothing(read);

        assertThat(subject.save(first, AT)).isTrue();
        assertThat(subject.save(second, AT)).isFalse();
        persistAndForget();

        assertThat(subject.load()).isEqualTo(first);
    }
}
