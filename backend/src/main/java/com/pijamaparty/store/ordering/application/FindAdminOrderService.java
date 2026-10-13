package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.api.AdminOrderDetails;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.StatusChange;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-18: {@link AdminOrderDetails} for the back-office order detail, read as the caller (the administrator). */
@Service
class FindAdminOrderService implements AdminOrderDetails {

    private final AdminOrderViews adminOrderViews;

    FindAdminOrderService(AdminOrderViews adminOrderViews) {
        this.adminOrderViews = adminOrderViews;
    }

    /** The order and its lines in one read transaction, with what the administrator may move it to. */
    @Override
    @Transactional(readOnly = true)
    public Optional<AdminOrder> find(UUID orderId) {
        return adminOrderViews.adminViewOf(OrderId.of(orderId)).map(order -> order.withNextStatuses(
            StatusChange.nextFor(OrderStatus.valueOf(order.status().name())).stream()
                .map(next -> Status.valueOf(next.name()))
                .toList()));
    }
}
