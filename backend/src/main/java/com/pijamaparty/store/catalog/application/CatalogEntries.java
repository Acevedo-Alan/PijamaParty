package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityEntry;
import com.pijamaparty.store.activity.api.ActivitySubject;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.activity.api.SubjectType;
import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.Variant;
import com.pijamaparty.store.platform.domain.Actor;
import java.util.List;

/**
 * UC-37: the activity entries of the catalog and home page use cases, labelled as the "Actividad" screen names them
 * (a category or product name, a SKU), never with personal data or file names (T-E8-02).
 */
final class CatalogEntries {

    private CatalogEntries() {
    }

    static ActivityEntry category(Actor administrator, ActivityAction action, Category category,
                                  List<FieldChange> changes) {
        return entry(administrator, action, ActivitySubject.of(SubjectType.CATEGORY, category.id().value(),
            category.name().value()), changes);
    }

    static ActivityEntry category(Actor administrator, ActivityAction action, Category category) {
        return category(administrator, action, category, List.of());
    }

    static ActivityEntry product(Actor administrator, ActivityAction action, Product product,
                                 List<FieldChange> changes) {
        return entry(administrator, action, ActivitySubject.of(SubjectType.PRODUCT, product.id().value(),
            product.details().name().value()), changes);
    }

    static ActivityEntry product(Actor administrator, ActivityAction action, Product product) {
        return product(administrator, action, product, List.of());
    }

    static ActivityEntry variant(Actor administrator, Variant variant, List<FieldChange> changes) {
        return entry(administrator, ActivityAction.VARIANT_UPDATED, ActivitySubject.of(SubjectType.VARIANT,
            variant.id().value(), variant.sku().value()), changes);
    }

    static ActivityEntry homeBanner(Actor administrator, ActivityAction action, List<FieldChange> changes) {
        return entry(administrator, action, ActivitySubject.of(SubjectType.HOME_BANNER), changes);
    }

    static ActivityEntry homeBanner(Actor administrator, ActivityAction action) {
        return homeBanner(administrator, action, List.of());
    }

    static ActivityEntry entry(Actor administrator, ActivityAction action, ActivitySubject subject,
                               List<FieldChange> changes) {
        return new ActivityEntry(administrator.userId().orElseThrow(
            () -> new IllegalArgumentException("only an administrator changes the catalog")), action, subject, changes);
    }
}
