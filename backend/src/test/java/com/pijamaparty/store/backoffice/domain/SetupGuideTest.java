package com.pijamaparty.store.backoffice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.pijamaparty.store.backoffice.domain.SetupFacts.Named;
import com.pijamaparty.store.backoffice.domain.SetupFacts.Thing;
import com.pijamaparty.store.backoffice.domain.SetupGuide.Entry;
import com.pijamaparty.store.backoffice.domain.SetupGuide.State;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** UC-46: each item's state from the store's data and the owner's omitted items. */
class SetupGuideTest {

    private static final SetupFacts COMPLETE = new SetupFacts(true, true, true, true, Named.NONE, Named.NONE);

    private static Named named(int total, String... names) {
        return new Named(List.of(names).stream().map(name -> new Thing(UUID.randomUUID(), name)).toList(), total);
    }

    private static Entry entry(SetupGuide guide, SetupItem item) {
        return guide.entries().stream().filter(entry -> entry.item() == item).findFirst().orElseThrow();
    }

    @Test
    void givenACompleteStore_whenTheGuideIsRead_thenItHasTheSixItemsInOrderAllDone() {
        SetupGuide guide = SetupGuide.from(COMPLETE, Set.of());

        assertThat(guide.entries()).extracting(Entry::item, Entry::state).containsExactly(
            tuple(SetupItem.WHATSAPP, State.DONE), tuple(SetupItem.EMAIL, State.DONE),
            tuple(SetupItem.SIZE_GUIDES, State.DONE), tuple(SetupItem.PRODUCT_PHOTOS, State.DONE),
            tuple(SetupItem.INSTAGRAM, State.DONE), tuple(SetupItem.HOME_BANNER, State.DONE));
        assertThat(guide.ready()).isEqualTo(6);
    }

    @Test
    void givenAStoreWithoutWhatsApp_whenTheGuideIsRead_thenThatItemIsPending() {
        SetupGuide guide = SetupGuide.from(new SetupFacts(false, true, true, true, Named.NONE, Named.NONE), Set.of());

        assertThat(entry(guide, SetupItem.WHATSAPP).state()).isEqualTo(State.PENDING);
        assertThat(guide.ready()).isEqualTo(5);
    }

    @Test
    void givenARopaDeCasaCategoryWithoutAGuide_whenTheGuideIsRead_thenTheItemNamesIt() {
        SetupGuide guide = SetupGuide.from(
            new SetupFacts(true, true, true, true, named(1, "Ropa de casa"), Named.NONE), Set.of());

        Entry sizeGuides = entry(guide, SetupItem.SIZE_GUIDES);
        assertThat(sizeGuides.state()).isEqualTo(State.PENDING);
        assertThat(sizeGuides.named().first()).extracting(Thing::name).containsExactly("Ropa de casa");
        assertThat(sizeGuides.named().total()).isEqualTo(1);
    }

    @Test
    void givenSevenProductsWithoutPhotos_whenTheGuideIsRead_thenItNamesFiveAndCountsSeven() {
        String[] seven = IntStream.rangeClosed(1, 7).mapToObj(n -> "Pijama " + n).toArray(String[]::new);

        Entry photos = entry(SetupGuide.from(
            new SetupFacts(true, true, true, true, Named.NONE, named(7, seven)), Set.of()), SetupItem.PRODUCT_PHOTOS);

        assertThat(photos.named().first()).extracting(Thing::name)
            .containsExactly("Pijama 1", "Pijama 2", "Pijama 3", "Pijama 4", "Pijama 5");
        assertThat(photos.named().total()).isEqualTo(7);
    }

    @Test
    void givenTheBannerHiddenAndOmitted_whenTheGuideIsRead_thenItIsOmittedAndCountsAsReady() {
        SetupGuide guide = SetupGuide.from(new SetupFacts(true, true, true, false, Named.NONE, Named.NONE),
            EnumSet.of(SetupItem.HOME_BANNER));

        assertThat(entry(guide, SetupItem.HOME_BANNER).state()).isEqualTo(State.OMITTED);
        assertThat(guide.ready()).isEqualTo(6);
    }

    @Test
    void givenAnItemAStoreNeedsStoredAsOmitted_whenTheGuideIsRead_thenItIsStillPending() {
        SetupGuide guide = SetupGuide.from(new SetupFacts(false, true, true, true, Named.NONE, Named.NONE),
            EnumSet.of(SetupItem.WHATSAPP));

        assertThat(entry(guide, SetupItem.WHATSAPP).state()).isEqualTo(State.PENDING);
    }

    @Test
    void givenOnlyInstagramAndTheBanner_whenAskedWhichMayBeOmitted_thenOnlyThoseTwo() {
        assertThat(EnumSet.allOf(SetupItem.class).stream().filter(SetupItem::omittable))
            .containsExactly(SetupItem.INSTAGRAM, SetupItem.HOME_BANNER);
    }
}
