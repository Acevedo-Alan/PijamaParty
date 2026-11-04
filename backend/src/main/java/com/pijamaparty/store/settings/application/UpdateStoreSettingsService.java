package com.pijamaparty.store.settings.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChanges;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.settings.domain.ContactEmail;
import com.pijamaparty.store.settings.domain.PlainText;
import com.pijamaparty.store.settings.domain.SocialLink;
import com.pijamaparty.store.settings.domain.StoreSettings;
import com.pijamaparty.store.settings.domain.WhatsAppNumber;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UpdateStoreSettingsService implements UpdateStoreSettings {

    private final LoadStoreSettings loadSettings;
    private final SaveStoreSettings saveSettings;
    private final SettingsActivity activity;
    private final Clock clock;

    UpdateStoreSettingsService(LoadStoreSettings loadSettings, SaveStoreSettings saveSettings,
                               SettingsActivity activity, Clock clock) {
        this.loadSettings = loadSettings;
        this.saveSettings = saveSettings;
        this.activity = activity;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        StoreSettings before = loadSettings.load();
        if (before.version() != command.version()) {
            return new Result.VersionChanged(before);
        }
        StoreSettings after;
        switch (before.edit(command.values())) {
            case StoreSettings.Edit.Invalid invalid -> {
                return new Result.Invalid(invalid.fields());
            }
            case StoreSettings.Edit.Edited edited -> after = edited.settings();
        }
        // The conditional write catches a save that got in between the read and this one.
        if (!saveSettings.save(after, clock.instant())) {
            return new Result.VersionChanged(loadSettings.load());
        }
        // UC-37: saving the same values changes nothing worth recording.
        if (!before.sameValuesAs(after)) {
            activity.record(new ActivityEntry(command.actor().userId().orElseThrow(),
                ActivityAction.STORE_SETTINGS_CHANGED, ActivitySubject.of(SubjectType.STORE_SETTINGS),
                changes(before, after).list()));
        }
        return new Result.Updated(after);
    }

    /** The store's business data, not personal data: each changed value as it reads in the store. */
    private static FieldChanges changes(StoreSettings before, StoreSettings after) {
        return new FieldChanges()
            .add("email", text(before.email().map(ContactEmail::value)), text(after.email().map(ContactEmail::value)))
            .add("whatsAppNumber", text(before.whatsAppNumber().map(WhatsAppNumber::digits)),
                text(after.whatsAppNumber().map(WhatsAppNumber::digits)))
            .add("openingHours", plain(before.openingHours()), plain(after.openingHours()))
            .add("address", plain(before.address()), plain(after.address()))
            .add("instagramUrl", link(before.instagram()), link(after.instagram()))
            .add("facebookUrl", link(before.facebook()), link(after.facebook()))
            .add("tiktokUrl", link(before.tiktok()), link(after.tiktok()))
            .add("announcement", plain(before.announcement()), plain(after.announcement()))
            .add("lowStockThreshold", String.valueOf(before.lowStockThreshold().units()),
                String.valueOf(after.lowStockThreshold().units()));
    }

    private static String plain(Optional<PlainText> text) {
        return text(text.map(PlainText::value));
    }

    private static String link(Optional<SocialLink> link) {
        return text(link.map(SocialLink::url));
    }

    private static String text(Optional<String> value) {
        return value.orElse(null);
    }
}
