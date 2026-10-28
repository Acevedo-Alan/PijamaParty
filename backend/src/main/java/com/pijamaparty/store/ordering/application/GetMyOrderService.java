package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.OrderLine;
import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;

/**
 * UC-22. Read as the customer itself, in a short transaction of the port's own; V14 hides other orders. Then the
 * photos of its lines from the catalog, in another (E7).
 */
@Service
class GetMyOrderService implements GetMyOrder {

    private final CustomerOrderViews customerOrderViews;
    private final LinePhotos linePhotos;

    GetMyOrderService(CustomerOrderViews customerOrderViews, LinePhotos linePhotos) {
        this.customerOrderViews = customerOrderViews;
        this.linePhotos = linePhotos;
    }

    @Override
    public Result execute(Query query) {
        if (!query.actor().is(Role.CUSTOMER) || query.actor().userId().isEmpty()) {
            return new Result.NotSignedIn();
        }
        return customerOrderViews.find(query.actor().userId().get(), query.order())
            .<Result>map(order -> new Result.Found(order,
                linePhotos.photosOf(order.lines().stream().map(OrderLine::variantId).distinct().toList())))
            .orElseGet(Result.NotFound::new);
    }
}
