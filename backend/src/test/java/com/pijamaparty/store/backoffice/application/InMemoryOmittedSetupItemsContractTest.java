package com.pijamaparty.store.backoffice.application;

class InMemoryOmittedSetupItemsContractTest extends OmittedSetupItemsContractTest<InMemoryOmittedSetupItems> {

    @Override
    protected InMemoryOmittedSetupItems createSubject() {
        return new InMemoryOmittedSetupItems();
    }
}
