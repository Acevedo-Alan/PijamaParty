package com.pijamaparty.store.backoffice.application;

/** Outbound read port (UC-46): which ways to reach the store "Datos de la tienda" has (UC-36). */
public interface StoreContactFacts {

    ContactFacts contact();

    record ContactFacts(boolean whatsApp, boolean email, boolean instagram) {}
}
