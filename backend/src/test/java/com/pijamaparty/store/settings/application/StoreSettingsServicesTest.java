package com.pijamaparty.store.settings.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.settings.domain.StoreSettings;
import com.pijamaparty.store.settings.domain.WhatsAppNumber;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UC-36: the use cases of "Datos de la tienda" against in-memory fakes. */
class StoreSettingsServicesTest {

    private static final Instant NOW = Instant.parse("2026-11-04T15:00:00Z");
    private static final UserId ADMIN_ID = UserId.of(UUID.randomUUID());
    private static final Actor ADMIN = Actor.admin(ADMIN_ID);
    private static final Actor CUSTOMER = Actor.customer(UserId.of(UUID.randomUUID()), Optional.empty(),
        Optional.empty());

    private final InMemoryStoreSettingsStore store = new InMemoryStoreSettingsStore();
    private final InMemorySettingsActivity activity = new InMemorySettingsActivity();
    private final UpdateStoreSettings update = new UpdateStoreSettingsService(store, store, activity,
        Clock.fixed(NOW, ZoneOffset.UTC));
    private final GetStoreSettings get = new GetStoreSettingsService(store);
    private final GetPublicStoreContact getContact = new GetPublicStoreContactService(store);
    private final com.pijamaparty.store.settings.api.StoreSettings reader = new StoreSettingsReader(store);

    private static StoreSettings.Typed typed(String whatsApp, String announcement, long threshold) {
        return new StoreSettings.Typed(null, whatsApp, null, null, null, null, null, announcement, threshold);
    }

    @Test
    void givenAWhatsAppNumberAsPeopleTypeIt_whenSaved_thenItIsStoredAsDigitsWithTheNextVersion() {
        var result = update.execute(new UpdateStoreSettings.Command(ADMIN, 0, typed("+54 9 342 400-0000", null, 3)));

        assertThat(result).isInstanceOf(UpdateStoreSettings.Result.Updated.class);
        StoreSettings saved = store.load();
        assertThat(saved.whatsAppNumber()).map(WhatsAppNumber::digits).contains("5493424000000");
        assertThat(saved.version()).isEqualTo(1);
        assertThat(store.updatedAt()).isEqualTo(NOW);
        assertThat(((UpdateStoreSettings.Result.Updated) result).settings()).isEqualTo(saved);
    }

    @Test
    void givenChangedValues_whenSaved_thenOneEntryHasEachChangedFieldBeforeAndAfter() {
        update.execute(new UpdateStoreSettings.Command(ADMIN, 0, typed("5493424000000", null, 3)));
        activity.clear();

        update.execute(new UpdateStoreSettings.Command(ADMIN, 1, typed("5493424000000",
            "Envío gratis a todo Santa Fe", 5)));

        var entry = activity.single();
        assertThat(entry.administrator()).isEqualTo(ADMIN_ID);
        assertThat(entry.action()).isEqualTo(ActivityAction.STORE_SETTINGS_CHANGED);
        assertThat(entry.subject()).isEqualTo(ActivitySubject.of(SubjectType.STORE_SETTINGS));
        assertThat(entry.changes()).containsExactly(
            FieldChange.of("announcement", null, "Envío gratis a todo Santa Fe"),
            FieldChange.of("lowStockThreshold", "3", "5"));
    }

    @Test
    void givenTheSameValues_whenSavedAgain_thenItIsSavedButNothingIsRecorded() {
        update.execute(new UpdateStoreSettings.Command(ADMIN, 0, typed("5493424000000", null, 3)));
        activity.clear();

        var result = update.execute(new UpdateStoreSettings.Command(ADMIN, 1, typed("+5493424000000", "  ", 3)));

        assertThat(result).isInstanceOf(UpdateStoreSettings.Result.Updated.class);
        assertThat(activity.entries()).isEmpty();
    }

    @Test
    void givenInvalidValues_whenSaved_thenTheFieldsAreNamedAndNothingIsSavedOrRecorded() {
        var result = update.execute(new UpdateStoreSettings.Command(ADMIN, 0, typed("123", null, 1001)));

        assertThat(result).isEqualTo(new UpdateStoreSettings.Result.Invalid(Set.of(
            StoreSettings.Field.WHATSAPP_NUMBER, StoreSettings.Field.LOW_STOCK_THRESHOLD)));
        assertThat(store.load()).isEqualTo(InMemoryStoreSettingsStore.EMPTY);
        assertThat(activity.entries()).isEmpty();
    }

    @Test
    void givenTwoTabsThatReadTheSameVersion_whenTheSecondSaves_thenItGetsTheCurrentSettingsAndNothingOfItIsSaved() {
        update.execute(new UpdateStoreSettings.Command(ADMIN, 0, typed("5493424000000", null, 3)));
        StoreSettings first = store.load();
        activity.clear();

        var result = update.execute(new UpdateStoreSettings.Command(ADMIN, 0, typed(null, "Otra pestaña", 9)));

        assertThat(result).isEqualTo(new UpdateStoreSettings.Result.VersionChanged(first));
        assertThat(store.load()).isEqualTo(first);
        assertThat(activity.entries()).isEmpty();
    }

    @Test
    void givenASaveThatGetsInBetweenTheReadAndTheWrite_whenTheWriteIsRefused_thenTheResultIsVersionChanged() {
        SaveStoreSettings racing = (settings, at) -> {
            store.replace(new StoreSettings(settings.email(), settings.whatsAppNumber(), settings.openingHours(),
                settings.address(), settings.instagram(), settings.facebook(), settings.tiktok(),
                settings.announcement(), settings.lowStockThreshold(), settings.version()));
            return false;
        };
        var racingUpdate = new UpdateStoreSettingsService(store, racing, activity, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = racingUpdate.execute(new UpdateStoreSettings.Command(ADMIN, 0, typed(null, null, 4)));

        assertThat(result).isInstanceOf(UpdateStoreSettings.Result.VersionChanged.class);
        assertThat(activity.entries()).isEmpty();
    }

    @Test
    void givenACustomer_whenSavingOrReading_thenItIsForbidden() {
        assertThat(update.execute(new UpdateStoreSettings.Command(CUSTOMER, 0, typed(null, null, 3))))
            .isInstanceOf(UpdateStoreSettings.Result.Forbidden.class);
        assertThat(get.execute(new GetStoreSettings.Query(CUSTOMER)))
            .isInstanceOf(GetStoreSettings.Result.Forbidden.class);
        assertThat(store.load()).isEqualTo(InMemoryStoreSettingsStore.EMPTY);
    }

    @Test
    void givenSavedSettings_whenReadByTheAdministratorTheStoreAndTheSummary_thenEachGetsThem() {
        update.execute(new UpdateStoreSettings.Command(ADMIN, 0, typed("5493424000000", null, 0)));

        assertThat(get.execute(new GetStoreSettings.Query(ADMIN)))
            .isEqualTo(new GetStoreSettings.Result.Found(store.load()));
        assertThat(getContact.execute()).isEqualTo(store.load());
        assertThat(reader.lowStockThreshold()).isZero();
    }
}
