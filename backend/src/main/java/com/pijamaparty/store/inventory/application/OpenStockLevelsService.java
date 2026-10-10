package com.pijamaparty.store.inventory.application;

import com.pijamaparty.store.inventory.api.StockLevels;
import java.time.Clock;
import java.util.Collection;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** UC-06: the stock of a new variant starts at 0; UC-07 records the initial load as a movement. */
@Service
class OpenStockLevelsService implements StockLevels {

    private final AddStockLevels addStockLevels;
    private final Clock clock;

    OpenStockLevelsService(AddStockLevels addStockLevels, Clock clock) {
        this.addStockLevels = addStockLevels;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void open(Collection<UUID> variantIds) {
        if (!variantIds.isEmpty()) {
            addStockLevels.addEmpty(variantIds, clock.instant());
        }
    }
}
