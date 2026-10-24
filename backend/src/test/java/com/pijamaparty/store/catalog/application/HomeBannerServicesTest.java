package com.pijamaparty.store.catalog.application;

import static com.pijamaparty.store.catalog.application.ProductFixture.ADMIN;
import static com.pijamaparty.store.catalog.application.ProductFixture.CUSTOMER;
import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.catalog.domain.HomeBanner;
import com.pijamaparty.store.catalog.domain.UploadKey;
import com.pijamaparty.store.platform.application.InMemoryIdGenerator;
import com.pijamaparty.store.platform.domain.Actor;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-32: the administrator edits, publishes and hides the character banner and replaces its photo. */
class HomeBannerServicesTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private static final Actor VISITOR = Actor.anonymous(Optional.empty(), Optional.empty());
    private static final byte[] PHOTO = TestImages.png(1600, 1200);
    private static final byte[] OTHER_PHOTO = TestImages.png(800, 800);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final InMemoryHomeBannerStore store = new InMemoryHomeBannerStore();
    private final InMemoryImageProcessor processor = new InMemoryImageProcessor();
    private final InMemoryImageStore files = new InMemoryImageStore();
    private final InMemoryCatalogActivity activity = new InMemoryCatalogActivity();
    private final GetHomeBanner getPublished = new GetHomeBannerService(store);
    private final GetHomeBannerForAdmin get = new GetHomeBannerForAdminService(store);
    private final UpdateHomeBanner update = new UpdateHomeBannerService(store, store, activity, clock);
    private final PublishHomeBanner publish = new PublishHomeBannerService(store, store, activity, clock);
    private final HideHomeBanner hide = new HideHomeBannerService(store, store, activity, clock);
    private final ReplaceHomeBannerPhoto replacePhoto = new ReplaceHomeBannerPhotoService(
        new HomeBannerPhotoTransactions(store, store, activity, clock), processor, files, new InMemoryIdGenerator());

    @Test
    void givenANewShop_whenTheHomePageAndTheAdministratorReadTheBanner_thenItIsHiddenAndEmpty() {
        assertThat(getPublished.execute()).isEqualTo(new GetHomeBanner.Result.NotPublished());
        assertThat(get.execute(new GetHomeBannerForAdmin.Query(ADMIN))).isEqualTo(new GetHomeBannerForAdmin.Result.Found(
            new HomeBannerView(Optional.empty(), Optional.empty(), false, InMemoryHomeBannerStore.CREATED_AT)));
    }

    @Test
    void givenABannerWithAPhotoTitleLinkTextAndTarget_whenPublished_thenTheHomePageShowsIt() {
        edit("/categoria/kigurumis");
        var photo = (ReplaceHomeBannerPhoto.Result.Replaced) upload(UUID.randomUUID(), PHOTO);

        var published = publish.execute(new PublishHomeBanner.Command(ADMIN));

        assertThat(published).isInstanceOfSatisfying(PublishHomeBanner.Result.Published.class,
            result -> assertThat(result.banner().published()).isTrue());
        assertThat(getPublished.execute()).isEqualTo(new GetHomeBanner.Result.Published(
            new GetHomeBanner.PublishedBanner("La noche se llena de personajes", "Una pijamada para jugar.",
                "Descubrí la colección", "/categoria/kigurumis", photo.banner().image().orElseThrow())));
    }

    @Test
    void givenAHiddenBanner_whenTheHomePageReadsIt_thenThereIsNone() {
        edit("/");
        upload(UUID.randomUUID(), PHOTO);
        publish.execute(new PublishHomeBanner.Command(ADMIN));

        assertThat(hide.execute(new HideHomeBanner.Command(ADMIN)))
            .isInstanceOfSatisfying(HideHomeBanner.Result.Hidden.class,
                hidden -> assertThat(hidden.banner().published()).isFalse());
        assertThat(getPublished.execute()).isEqualTo(new GetHomeBanner.Result.NotPublished());
    }

    @Test
    void givenABannerWithoutAPhoto_whenPublished_thenItIsRefusedNamingThePhotoAndStaysHidden() {
        edit("/");

        assertThat(publish.execute(new PublishHomeBanner.Command(ADMIN)))
            .isEqualTo(new PublishHomeBanner.Result.Incomplete(Set.of(HomeBanner.Part.PHOTO)));
        assertThat(getPublished.execute()).isEqualTo(new GetHomeBanner.Result.NotPublished());
    }

    @Test
    void givenATargetOutsideTheStore_whenSaved_thenItIsRefusedAndNothingChanges() {
        edit("/");

        for (String target : new String[] {"https://other.site", "//other.site", "/\\other.site"}) {
            assertThat(update.execute(new UpdateHomeBanner.Command(ADMIN, "Otro", "", "Ver", target)))
                .isEqualTo(new UpdateHomeBanner.Result.Invalid(Set.of(HomeBanner.Field.LINK_PATH)));
        }
        assertThat(store.load().texts().orElseThrow().title()).isEqualTo("La noche se llena de personajes");
    }

    @Test
    void givenAPhoto_whenAnotherReplacesIt_thenTheOldFilesAreDeletedAndTheBannerShowsTheNewOne() {
        var first = (ReplaceHomeBannerPhoto.Result.Replaced) upload(UUID.randomUUID(), PHOTO);
        Set<String> firstFiles = files.fileNames();

        var second = upload(UUID.randomUUID(), OTHER_PHOTO);

        assertThat(second).isInstanceOf(ReplaceHomeBannerPhoto.Result.Replaced.class);
        var image = ((ReplaceHomeBannerPhoto.Result.Replaced) second).banner().image().orElseThrow();
        assertThat(image).isNotEqualTo(first.banner().image().orElseThrow());
        assertThat(files.fileNames()).hasSize(3).doesNotContainAnyElementsOf(firstFiles)
            .containsExactlyInAnyOrderElementsOf(image.files().stream().map(ProductView.ImageFile::fileName).toList());
    }

    @Test
    void givenAnUploadRetriedWithTheSameKey_whenTheFileIsTheSameOrAnother_thenItIsARetryOrRefused() {
        UUID key = UUID.randomUUID();
        var first = (ReplaceHomeBannerPhoto.Result.Replaced) upload(key, PHOTO);

        assertThat(upload(key, PHOTO)).isEqualTo(new ReplaceHomeBannerPhoto.Result.AlreadyUploaded(first.banner()));
        assertThat(upload(key, OTHER_PHOTO)).isEqualTo(new ReplaceHomeBannerPhoto.Result.KeyReused());
        assertThat(processor.conversions()).isEqualTo(1);
        assertThat(files.fileNames()).hasSize(3);
    }

    @Test
    void givenFilesThatAreNotAcceptedPhotos_whenUploaded_thenTheyAreRefusedAndNothingIsStored() {
        assertThat(upload(UUID.randomUUID(), "not a photo".getBytes(StandardCharsets.UTF_8)))
            .isEqualTo(new ReplaceHomeBannerPhoto.Result.UnsupportedFormat());
        assertThat(upload(UUID.randomUUID(), TestImages.pngHeaderOnly(20_000, 20)))
            .isEqualTo(new ReplaceHomeBannerPhoto.Result.TooLarge());

        assertThat(files.fileNames()).isEmpty();
        assertThat(store.load().photo()).isEmpty();
    }

    @Test
    void givenAVisitorOrACustomer_whenTheyTryToChangeTheBanner_thenTheyAreForbiddenAndNothingChanges() {
        for (Actor actor : new Actor[] {VISITOR, CUSTOMER}) {
            assertThat(get.execute(new GetHomeBannerForAdmin.Query(actor)))
                .isEqualTo(new GetHomeBannerForAdmin.Result.Forbidden());
            assertThat(update.execute(new UpdateHomeBanner.Command(actor, "Título", "", "Ver", "/")))
                .isEqualTo(new UpdateHomeBanner.Result.Forbidden());
            assertThat(publish.execute(new PublishHomeBanner.Command(actor)))
                .isEqualTo(new PublishHomeBanner.Result.Forbidden());
            assertThat(hide.execute(new HideHomeBanner.Command(actor))).isEqualTo(new HideHomeBanner.Result.Forbidden());
            assertThat(replacePhoto.execute(new ReplaceHomeBannerPhoto.Command(actor, UploadKey.of(UUID.randomUUID()),
                PHOTO))).isEqualTo(new ReplaceHomeBannerPhoto.Result.Forbidden());
        }
        assertThat(store.load().texts()).isEmpty();
        assertThat(files.fileNames()).isEmpty();
    }

    private void edit(String target) {
        assertThat(update.execute(new UpdateHomeBanner.Command(ADMIN, " La noche se llena de personajes ",
            "Una pijamada para jugar.", "Descubrí la colección", target)))
            .isInstanceOf(UpdateHomeBanner.Result.Updated.class);
    }

    private ReplaceHomeBannerPhoto.Result upload(UUID key, byte[] content) {
        return replacePhoto.execute(new ReplaceHomeBannerPhoto.Command(ADMIN, UploadKey.of(key), content));
    }
}
