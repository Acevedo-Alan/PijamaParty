package com.pijamaparty.store.backoffice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.pijamaparty.store.backoffice.domain.SetupFacts.Thing;
import com.pijamaparty.store.backoffice.domain.SetupGuide;
import com.pijamaparty.store.backoffice.domain.SetupGuide.Entry;
import com.pijamaparty.store.backoffice.domain.SetupGuide.State;
import com.pijamaparty.store.backoffice.domain.SetupItem;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** UC-46: the setup guide of the summary, omitting an item and showing it again. */
class SetupGuideServicesTest {

    private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));
    private static final Actor CUSTOMER = Actor.customer(UserId.of(UUID.randomUUID()), Optional.empty(),
        Optional.empty());

    private final InMemoryStoreContactFacts contact = new InMemoryStoreContactFacts();
    private final InMemoryCatalogSetupFacts catalog = new InMemoryCatalogSetupFacts();
    private final InMemoryOmittedSetupItems omitted = new InMemoryOmittedSetupItems();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-24T15:00:00Z"), ZoneOffset.UTC);

    private final GetSetupGuideService getGuide = new GetSetupGuideService(contact, catalog, omitted);
    private final OmitSetupItemService omit = new OmitSetupItemService(omitted, clock);
    private final ShowSetupItemService show = new ShowSetupItemService(omitted);

    private SetupGuide guide() {
        return ((GetSetupGuide.Result.Shown) getGuide.execute(new GetSetupGuide.Query(ADMIN))).guide();
    }

    private Entry entry(SetupItem item) {
        return guide().entries().stream().filter(entry -> entry.item() == item).findFirst().orElseThrow();
    }

    @Test
    void givenAStoreWithoutWhatsApp_whenTheSummaryOpens_thenTheItemIsPending_andOnceSavedItIsDone() {
        contact.set(false, true, true);
        assertThat(entry(SetupItem.WHATSAPP).state()).isEqualTo(State.PENDING);

        contact.set(true, true, true);

        assertThat(entry(SetupItem.WHATSAPP).state()).isEqualTo(State.DONE);
    }

    @Test
    void givenPijamasWithAGuide_RopaDeCasaWithoutOne_andBatasWithoutProducts_whenTheSummaryOpens_thenOnlyRopaDeCasa() {
        catalog.product(catalog.category("Pijamas", 0, true, true), "Luna", true, 1, true);
        UUID ropaDeCasa = catalog.category("Ropa de casa", 1, true, false);
        catalog.product(ropaDeCasa, "Remerón", true, 1, true);
        catalog.category("Batas", 2, true, false);

        assertThat(entry(SetupItem.SIZE_GUIDES).named().first()).containsExactly(new Thing(ropaDeCasa, "Ropa de casa"));
    }

    @Test
    void givenSevenPublishedProductsWithoutPhotosAndAHiddenOne_whenTheSummaryOpens_thenFiveNewestAndATotalOf7() {
        UUID category = catalog.category("Pijamas", 0, true, true);
        IntStream.rangeClosed(1, 7).forEach(n -> catalog.product(category, "Pijama " + n, true, n, false));
        catalog.product(category, "Oculto", false, 0, false);

        Entry photos = entry(SetupItem.PRODUCT_PHOTOS);

        assertThat(photos.named().first()).extracting(Thing::name)
            .containsExactly("Pijama 1", "Pijama 2", "Pijama 3", "Pijama 4", "Pijama 5");
        assertThat(photos.named().total()).isEqualTo(7);
    }

    @Test
    void givenTheBannerHidden_whenItIsOmitted_thenItIsOmittedAndCountsAsReady() {
        contact.set(true, true, true);
        long before = guide().ready();

        assertThat(omit.execute(new OmitSetupItem.Command(ADMIN, SetupItem.HOME_BANNER)))
            .isEqualTo(new OmitSetupItem.Result.Omitted());

        assertThat(entry(SetupItem.HOME_BANNER).state()).isEqualTo(State.OMITTED);
        assertThat(guide().ready()).isEqualTo(before + 1);
    }

    @Test
    void givenAnOmittedItem_whenShownAgain_thenItIsPendingOrDoneByItsData() {
        omit.execute(new OmitSetupItem.Command(ADMIN, SetupItem.INSTAGRAM));

        assertThat(show.execute(new ShowSetupItem.Command(ADMIN, SetupItem.INSTAGRAM)))
            .isEqualTo(new ShowSetupItem.Result.Shown());

        assertThat(entry(SetupItem.INSTAGRAM).state()).isEqualTo(State.PENDING);
    }

    @Test
    void givenAnItemAWorkingStoreNeeds_whenOmitted_thenItIsNotOmittableAndNothingIsStored() {
        for (SetupItem item : EnumSet.of(SetupItem.WHATSAPP, SetupItem.EMAIL, SetupItem.SIZE_GUIDES,
            SetupItem.PRODUCT_PHOTOS)) {
            assertThat(omit.execute(new OmitSetupItem.Command(ADMIN, item)))
                .isEqualTo(new OmitSetupItem.Result.NotOmittable());
        }
        assertThat(omitted.omitted()).isEmpty();
    }

    @Test
    void givenTheSameItemOmittedTwice_whenRead_thenBothAreOmittedAndItIsStoredOnce() {
        omit.execute(new OmitSetupItem.Command(ADMIN, SetupItem.HOME_BANNER));

        assertThat(omit.execute(new OmitSetupItem.Command(ADMIN, SetupItem.HOME_BANNER)))
            .isEqualTo(new OmitSetupItem.Result.Omitted());
        assertThat(omitted.omitted()).containsExactly(SetupItem.HOME_BANNER);
    }

    @Test
    void givenEverythingDone_whenTheSummaryOpens_thenEveryItemIsDone() {
        contact.set(true, true, true);
        catalog.banner(true);

        assertThat(guide().entries()).extracting(Entry::state).containsOnly(State.DONE);
        assertThat(guide().entries()).extracting(Entry::item, Entry::state)
            .startsWith(tuple(SetupItem.WHATSAPP, State.DONE));
    }

    @Test
    void givenACustomerOrNobody_whenUsingTheGuide_thenItIsForbiddenAndNothingIsStored() {
        Actor anonymous = Actor.anonymous(Optional.empty(), Optional.empty());

        assertThat(getGuide.execute(new GetSetupGuide.Query(CUSTOMER))).isEqualTo(new GetSetupGuide.Result.Forbidden());
        assertThat(getGuide.execute(new GetSetupGuide.Query(anonymous))).isEqualTo(new GetSetupGuide.Result.Forbidden());
        assertThat(omit.execute(new OmitSetupItem.Command(CUSTOMER, SetupItem.HOME_BANNER)))
            .isEqualTo(new OmitSetupItem.Result.Forbidden());
        assertThat(show.execute(new ShowSetupItem.Command(CUSTOMER, SetupItem.HOME_BANNER)))
            .isEqualTo(new ShowSetupItem.Result.Forbidden());
        assertThat(omitted.omitted()).isEmpty();
    }
}
