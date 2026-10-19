package com.pijamaparty.store.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.ContentHash;
import com.pijamaparty.store.catalog.domain.HomeBanner;
import com.pijamaparty.store.catalog.domain.HomeBannerPhoto;
import com.pijamaparty.store.catalog.domain.ImagePlaceholder;
import com.pijamaparty.store.catalog.domain.StorageKey;
import com.pijamaparty.store.catalog.domain.UploadKey;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link LoadHomeBanner} and {@link SaveHomeBanner}: one banner, there from the start. */
public abstract class HomeBannerStoreContractTest<T extends LoadHomeBanner & SaveHomeBanner> extends ContractTest<T> {

    /** Whole seconds: PostgreSQL keeps microseconds, so the instants survive the round trip. */
    private static final Instant SAVED_AT = Instant.parse("2026-10-19T12:00:00Z");

    @Test
    void givenANewStore_whenTheBannerIsLoaded_thenItIsHiddenWithoutTextsOrPhoto() {
        HomeBanner banner = subject.load();

        assertThat(banner.published()).isFalse();
        assertThat(banner.texts()).isEmpty();
        assertThat(banner.photo()).isEmpty();
        assertThat(subject.lock().texts()).isEmpty();
    }

    @Test
    void givenAPublishedBanner_whenSavedAndLoaded_thenItHasTheSameTextsPhotoAndState() {
        HomeBanner banner = subject.lock();
        banner.edit("La noche se llena de personajes", "Una pijamada para jugar,\nsoñar y descansar.",
            "Descubrí la colección", "/?publico=infantil", SAVED_AT);
        banner.replacePhoto(photo("first"), SAVED_AT);
        banner.publish(SAVED_AT.plusSeconds(60));

        subject.save(banner);
        persistAndForget();

        assertThat(subject.load()).usingRecursiveComparison().isEqualTo(banner);
        assertThat(subject.lock()).usingRecursiveComparison().isEqualTo(banner);
    }

    @Test
    void givenAPublishedBanner_whenItsPhotoIsReplacedAndItIsHidden_thenTheStoreKeepsOnlyTheNewState() {
        HomeBanner banner = subject.lock();
        banner.edit("Personajes", "", "Ver", "/", SAVED_AT);
        banner.replacePhoto(photo("first"), SAVED_AT);
        banner.publish(SAVED_AT);
        subject.save(banner);
        persistAndForget();

        HomeBanner changed = subject.lock();
        HomeBannerPhoto second = photo("second");
        changed.replacePhoto(second, SAVED_AT.plusSeconds(60));
        changed.hide(SAVED_AT.plusSeconds(120));
        subject.save(changed);
        persistAndForget();

        HomeBanner loaded = subject.load();
        assertThat(loaded.photo()).contains(second);
        assertThat(loaded.published()).isFalse();
        assertThat(loaded.updatedAt()).isEqualTo(SAVED_AT.plusSeconds(120));
    }

    @Test
    void givenALoadedBanner_whenChangedWithoutSaving_thenTheStoredOneStaysAsItWas() {
        subject.load().edit("Personajes", "", "Ver", "/", SAVED_AT);

        assertThat(subject.load().texts()).isEmpty();
    }

    private static HomeBannerPhoto photo(String content) {
        return new HomeBannerPhoto(StorageKey.of(UUID.randomUUID()),
            new ImagePlaceholder("data:image/webp;base64,UklGRg=="), UploadKey.of(UUID.randomUUID()),
            ContentHash.of(content.getBytes(StandardCharsets.UTF_8)));
    }
}
