package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.backoffice.application.StoreContactFacts.ContactFacts;
import com.pijamaparty.store.backoffice.domain.SetupFacts;
import com.pijamaparty.store.backoffice.domain.SetupGuide;
import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetSetupGuideService implements GetSetupGuide {

    private final StoreContactFacts contactFacts;
    private final CatalogSetupFacts catalogFacts;
    private final LoadOmittedSetupItems omittedItems;

    GetSetupGuideService(StoreContactFacts contactFacts, CatalogSetupFacts catalogFacts,
                         LoadOmittedSetupItems omittedItems) {
        this.contactFacts = contactFacts;
        this.catalogFacts = catalogFacts;
        this.omittedItems = omittedItems;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        ContactFacts contact = contactFacts.contact();
        SetupFacts facts = new SetupFacts(contact.whatsApp(), contact.email(), contact.instagram(),
            catalogFacts.homeBannerPublished(), catalogFacts.categoriesWithoutSizeGuide(SetupGuide.NAMED),
            catalogFacts.publishedProductsWithoutPhoto(SetupGuide.NAMED));
        return new Result.Shown(SetupGuide.from(facts, omittedItems.omitted()));
    }
}
