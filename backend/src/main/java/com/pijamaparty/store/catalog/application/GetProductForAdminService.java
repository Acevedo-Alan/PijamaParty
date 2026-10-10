package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetProductForAdminService implements GetProductForAdmin {

    private final LoadProducts loadProducts;

    GetProductForAdminService(LoadProducts loadProducts) {
        this.loadProducts = loadProducts;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        return loadProducts.findById(query.productId())
            .<Result>map(product -> new Result.Found(ProductView.of(product)))
            .orElseGet(Result.NotFound::new);
    }
}
