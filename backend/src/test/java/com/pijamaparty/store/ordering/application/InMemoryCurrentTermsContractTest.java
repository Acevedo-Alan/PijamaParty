package com.pijamaparty.store.ordering.application;

class InMemoryCurrentTermsContractTest extends CurrentTermsContractTest<InMemoryCurrentTerms> {

    @Override
    protected InMemoryCurrentTerms createSubject() {
        return new InMemoryCurrentTerms(VERSION);
    }
}
