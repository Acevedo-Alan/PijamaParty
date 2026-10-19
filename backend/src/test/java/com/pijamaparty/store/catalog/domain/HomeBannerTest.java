package com.pijamaparty.store.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** UC-32: the rules of the character banner, its link target and its photo. */
class HomeBannerTest {

    private static final Instant CREATED = Instant.parse("2026-10-19T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private static final ImagePlaceholder PLACEHOLDER = new ImagePlaceholder("data:image/webp;base64,AAAA");

    private final HomeBanner banner = HomeBanner.restore(Optional.empty(), Optional.empty(), false, CREATED);

    @Test
    void givenTextsWithSpaces_whenEdited_thenTheyAreStoredTrimmed() {
        assertThat(banner.edit("  La noche se llena de personajes ", " Una pijamada para jugar.\n ", " Ver ",
            " /categoria/kigurumis ", NOW)).isEqualTo(new HomeBanner.EditOutcome.Edited());

        assertThat(banner.texts()).contains(new HomeBanner.Texts("La noche se llena de personajes",
            "Una pijamada para jugar.", "Ver", new StorePath("/categoria/kigurumis")));
        assertThat(banner.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void givenTextsAtTheirLimits_whenEdited_thenTheyAreAcceptedAndOneMoreCharacterIsRefused() {
        assertThat(banner.edit("a".repeat(60), "b".repeat(200), "c".repeat(30), "/" + "d".repeat(199), NOW))
            .isEqualTo(new HomeBanner.EditOutcome.Edited());
        assertThat(banner.edit("a", "", "c", "/", NOW)).isEqualTo(new HomeBanner.EditOutcome.Edited());

        assertThat(banner.edit("a".repeat(61), "b".repeat(201), "c".repeat(31), "/" + "d".repeat(200), NOW))
            .isEqualTo(new HomeBanner.EditOutcome.Invalid(Set.of(HomeBanner.Field.values())));
        assertThat(banner.edit("  ", null, " ", null, NOW))
            .isEqualTo(new HomeBanner.EditOutcome.Invalid(Set.of(HomeBanner.Field.values())));
    }

    @Test
    void givenSavedTexts_whenAnInvalidEditArrives_thenNothingChanges() {
        banner.edit("Personajes", "", "Ver", "/", NOW);

        assertThat(banner.edit("Otro título", "", "Ver", "https://other.site", NOW.plusSeconds(60)))
            .isEqualTo(new HomeBanner.EditOutcome.Invalid(Set.of(HomeBanner.Field.LINK_PATH)));
        assertThat(banner.texts().orElseThrow().title()).isEqualTo("Personajes");
        assertThat(banner.updatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://other.site", "//other.site", "/\\other.site", "\\\\other.site", "other.site",
        "javascript:alert(1)", "/kigu\nrumis", "/kigu\u0000rumis", "/kigu​rumis", ""})
    void givenATargetOutsideTheStore_whenParsed_thenItIsRefused(String target) {
        assertThat(StorePath.parse(target)).isEmpty();
        assertThatThrownBy(() -> new StorePath(target)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/categoria/kigurumis", "/?publico=infantil&temporada=invierno", "/producto/pijama-oso"})
    void givenAPathOfTheStore_whenParsed_thenItIsAccepted(String target) {
        assertThat(StorePath.parse(" " + target + " ")).contains(new StorePath(target));
    }

    @Test
    void givenANewBanner_whenPublished_thenItIsRefusedNamingEverythingMissing() {
        assertThat(banner.publish(NOW)).isEqualTo(new HomeBanner.PublishOutcome.Incomplete(
            Set.of(HomeBanner.Part.PHOTO, HomeBanner.Part.TITLE, HomeBanner.Part.LINK_TEXT, HomeBanner.Part.LINK_PATH)));
        assertThat(banner.published()).isFalse();
    }

    @Test
    void givenTextsWithoutAPhoto_whenPublished_thenItIsRefusedNamingThePhoto() {
        banner.edit("Personajes", "", "Ver", "/", NOW);

        assertThat(banner.publish(NOW))
            .isEqualTo(new HomeBanner.PublishOutcome.Incomplete(Set.of(HomeBanner.Part.PHOTO)));
        assertThat(banner.published()).isFalse();
    }

    @Test
    void givenACompleteBanner_whenPublishedAndHidden_thenItIsShownThenHidden() {
        banner.edit("Personajes", "", "Ver", "/", CREATED);
        banner.replacePhoto(photo(UUID.randomUUID(), "a"), CREATED);

        assertThat(banner.publish(NOW)).isEqualTo(new HomeBanner.PublishOutcome.Published());
        assertThat(banner.published()).isTrue();
        assertThat(banner.updatedAt()).isEqualTo(NOW);
        assertThat(banner.publish(NOW.plusSeconds(60))).isEqualTo(new HomeBanner.PublishOutcome.Published());
        assertThat(banner.updatedAt()).as("publishing again changes nothing").isEqualTo(NOW);

        banner.hide(NOW.plusSeconds(120));
        banner.hide(NOW.plusSeconds(180));
        assertThat(banner.published()).isFalse();
        assertThat(banner.updatedAt()).isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    void givenAPhoto_whenAnotherReplacesIt_thenThePreviousOneIsReturnedSoItsFilesCanGo() {
        HomeBannerPhoto first = photo(UUID.randomUUID(), "a");
        HomeBannerPhoto second = photo(UUID.randomUUID(), "b");

        assertThat(banner.replacePhoto(first, CREATED))
            .isEqualTo(new HomeBanner.PhotoUpload.Replaced(first, Optional.empty()));
        assertThat(banner.replacePhoto(second, NOW))
            .isEqualTo(new HomeBanner.PhotoUpload.Replaced(second, Optional.of(first)));
        assertThat(banner.photo()).contains(second);
        assertThat(banner.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void givenTheUploadKeyOfTheCurrentPhoto_whenCheckedWithTheSameOrAnotherFile_thenItIsARetryOrAReusedKey() {
        UUID key = UUID.randomUUID();
        HomeBannerPhoto current = photo(key, "a");
        banner.replacePhoto(current, CREATED);

        assertThat(banner.checkPhoto(UploadKey.of(key), hash("a")))
            .isEqualTo(new HomeBanner.PhotoUpload.AlreadyUploaded(current));
        assertThat(banner.checkPhoto(UploadKey.of(key), hash("b"))).isEqualTo(new HomeBanner.PhotoUpload.KeyReused());
        assertThat(banner.replacePhoto(photo(key, "b"), NOW)).isEqualTo(new HomeBanner.PhotoUpload.KeyReused());
        assertThat(banner.checkPhoto(UploadKey.of(UUID.randomUUID()), hash("a")))
            .isEqualTo(new HomeBanner.PhotoUpload.Ready());
        assertThat(banner.photo()).contains(current);
    }

    @Test
    void givenAStoredBannerPublishedWithoutAPhoto_whenRestored_thenItIsRefused() {
        var texts = new HomeBanner.Texts("Personajes", "", "Ver", new StorePath("/"));

        assertThatThrownBy(() -> HomeBanner.restore(Optional.of(texts), Optional.empty(), true, CREATED))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static HomeBannerPhoto photo(UUID uploadKey, String content) {
        return new HomeBannerPhoto(StorageKey.of(UUID.randomUUID()), PLACEHOLDER, UploadKey.of(uploadKey),
            hash(content));
    }

    private static ContentHash hash(String content) {
        return ContentHash.of(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
