package com.pijamaparty.store.backoffice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.backoffice.application.StoreContactFacts.ContactFacts;
import com.pijamaparty.store.platform.testing.ContractTest;
import org.junit.jupiter.api.Test;

/** Contract of {@link StoreContactFacts} (UC-46): whether "Datos de la tienda" has each way to reach the store. */
public abstract class StoreContactFactsContractTest<T extends StoreContactFacts> extends ContractTest<T> {

    /** The store's settings with or without each value. */
    protected abstract void givenContact(boolean whatsApp, boolean email, boolean instagram);

    @Test
    void givenNoContactYet_whenRead_thenItHasNone() {
        givenContact(false, false, false);
        persistAndForget();

        assertThat(subject.contact()).isEqualTo(new ContactFacts(false, false, false));
    }

    @Test
    void givenTheWhatsAppNumberAndTheInstagramLink_whenRead_thenItHasThoseTwo() {
        givenContact(true, false, true);
        persistAndForget();

        assertThat(subject.contact()).isEqualTo(new ContactFacts(true, false, true));
    }
}
