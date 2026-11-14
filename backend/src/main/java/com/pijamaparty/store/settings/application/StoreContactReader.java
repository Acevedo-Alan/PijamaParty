package com.pijamaparty.store.settings.application;

import com.pijamaparty.store.settings.api.StoreContact;
import com.pijamaparty.store.settings.domain.ContactEmail;
import com.pijamaparty.store.settings.domain.SocialLink;
import com.pijamaparty.store.settings.domain.StoreSettings;
import com.pijamaparty.store.settings.domain.WhatsAppNumber;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link StoreContact} for the other modules (T-E3-33): the contact of the emails' footer. */
@Service
class StoreContactReader implements StoreContact {

    private final LoadStoreSettings loadSettings;

    StoreContactReader(LoadStoreSettings loadSettings) {
        this.loadSettings = loadSettings;
    }

    @Override
    @Transactional(readOnly = true)
    public Contact contact() {
        StoreSettings settings = loadSettings.load();
        return new Contact(
            settings.email().map(ContactEmail::value),
            settings.whatsAppNumber().map(WhatsAppNumber::digits),
            settings.instagram().map(SocialLink::url),
            settings.facebook().map(SocialLink::url),
            settings.tiktok().map(SocialLink::url));
    }
}
