package com.pijamaparty.store.backoffice.application;

/** Fake of {@link StoreContactFacts}: the contact the test sets, none at first. */
public class InMemoryStoreContactFacts implements StoreContactFacts {

    private ContactFacts contact = new ContactFacts(false, false, false);

    public void set(boolean whatsApp, boolean email, boolean instagram) {
        contact = new ContactFacts(whatsApp, email, instagram);
    }

    @Override
    public ContactFacts contact() {
        return contact;
    }
}
