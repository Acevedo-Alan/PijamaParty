package com.pijamaparty.store.backoffice.application;

class InMemoryStoreContactFactsContractTest extends StoreContactFactsContractTest<InMemoryStoreContactFacts> {

    @Override
    protected InMemoryStoreContactFacts createSubject() {
        return new InMemoryStoreContactFacts();
    }

    @Override
    protected void givenContact(boolean whatsApp, boolean email, boolean instagram) {
        subject.set(whatsApp, email, instagram);
    }
}
