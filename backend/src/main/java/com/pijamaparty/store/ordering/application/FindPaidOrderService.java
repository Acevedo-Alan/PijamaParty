package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.PaidOrders;
import com.pijamaparty.store.ordering.domain.OrderId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-17: {@link PaidOrders} for the payment-confirmed emails, read as the caller (the system's email job). */
@Service
class FindPaidOrderService implements PaidOrders {

    private final PaidOrderDetails details;

    FindPaidOrderService(PaidOrderDetails details) {
        this.details = details;
    }

    /** The order and its lines in one read transaction. */
    @Override
    @Transactional(readOnly = true)
    public Optional<PaidOrder> find(UUID orderId) {
        return details.detailsOf(OrderId.of(orderId));
    }
}
