package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.backoffice.domain.OrderAlert;
import com.pijamaparty.store.ordering.api.AdminOrderDetails.AdminOrder;
import com.pijamaparty.store.ordering.api.OrderWithdrawals.ActiveWithdrawal;
import com.pijamaparty.store.platform.domain.Role;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetOrderDetailService implements GetOrderDetail {

    private final OrderDetails orderDetails;
    private final BuyerEmails buyerEmails;
    private final WithdrawalRequests withdrawalRequests;

    GetOrderDetailService(OrderDetails orderDetails, BuyerEmails buyerEmails, WithdrawalRequests withdrawalRequests) {
        this.orderDetails = orderDetails;
        this.buyerEmails = buyerEmails;
        this.withdrawalRequests = withdrawalRequests;
    }

    /** The order, its withdrawal request and its email's status in one read transaction. */
    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        Optional<AdminOrder> found = orderDetails.find(query.orderId());
        if (found.isEmpty()) {
            return new Result.NotFound();
        }
        AdminOrder order = found.get();
        Optional<ActiveWithdrawal> withdrawal = withdrawalRequests.activeOf(query.orderId());
        return new Result.Found(order, withdrawal, OrderAlert.of(withdrawal.isPresent(), order.paidAfterClosing(),
            buyerEmails.problemOf(query.orderId())));
    }
}
