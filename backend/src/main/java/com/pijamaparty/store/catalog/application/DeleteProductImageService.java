package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;

/** Not transactional on purpose: the files are removed only after the photo's removal is committed (UC-06). */
@Service
class DeleteProductImageService implements DeleteProductImage {

    private final ProductImageTransactions transactions;
    private final ImageStore imageStore;

    DeleteProductImageService(ProductImageTransactions transactions, ImageStore imageStore) {
        this.transactions = transactions;
        this.imageStore = imageStore;
    }

    @Override
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        return transactions.remove(command.productId(), command.imageId())
            .<Result>map(removed -> {
                imageStore.delete(removed.storageKey());
                return new Result.Deleted();
            })
            .orElseGet(Result.NotFound::new);
    }
}
