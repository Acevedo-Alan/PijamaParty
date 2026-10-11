package com.pijamaparty.store.catalog.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class RefreshSearchVectorService implements RefreshSearchVector {

    private final SearchVectors searchVectors;

    RefreshSearchVectorService(SearchVectors searchVectors) {
        this.searchVectors = searchVectors;
    }

    /** Always within the write that changed the texts, so the index commits or rolls back with it. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void execute(Command command) {
        switch (command) {
            case Command.OfProduct product -> searchVectors.refreshProduct(product.product());
            case Command.OfCategory category -> searchVectors.refreshCategory(category.category());
        }
    }
}
