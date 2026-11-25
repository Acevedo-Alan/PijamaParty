package com.pijamaparty.store.backoffice.adapter.out.settings;

import com.pijamaparty.store.backoffice.application.StoreContactFacts;
import com.pijamaparty.store.settings.api.StoreContact;
import com.pijamaparty.store.settings.api.StoreContact.Contact;
import org.springframework.stereotype.Component;

/** Reads "Datos de la tienda" through the settings module's public API, in the caller's transaction (UC-46). */
@Component
class SettingsStoreContactFacts implements StoreContactFacts {

    private final StoreContact storeContact;

    SettingsStoreContactFacts(StoreContact storeContact) {
        this.storeContact = storeContact;
    }

    @Override
    public ContactFacts contact() {
        Contact contact = storeContact.contact();
        return new ContactFacts(contact.whatsAppNumber().isPresent(), contact.email().isPresent(),
            contact.instagramUrl().isPresent());
    }
}
