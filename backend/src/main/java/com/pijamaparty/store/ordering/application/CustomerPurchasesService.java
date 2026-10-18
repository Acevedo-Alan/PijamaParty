package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.CustomerPurchases;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-24: {@link CustomerPurchases} for the reviews module, read as the caller. */
@Service
class CustomerPurchasesService implements CustomerPurchases {

    private static final Set<OrderStatus> BOUGHT = Arrays.stream(OrderStatus.values())
        .filter(OrderStatus::isBought)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(OrderStatus.class)));

    private final PurchasedVariants purchases;

    CustomerPurchasesService(PurchasedVariants purchases) {
        this.purchases = purchases;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean boughtAnyOf(UserId customer, Collection<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return false;
        }
        return purchases.hasOrderWithAny(customer, BOUGHT, Set.copyOf(variantIds));
    }
}
