package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.activity.api.ActivityEntry;
import java.util.List;

class InMemoryStockActivityContractTest extends StockActivityContractTest<InMemoryStockActivity> {

    @Override
    protected InMemoryStockActivity createSubject() {
        return new InMemoryStockActivity();
    }

    @Override
    protected List<ActivityEntry> recorded() {
        return subject.entries();
    }
}
