package com.pijamaparty.store.backoffice.application;

import com.pijamaparty.store.backoffice.domain.OrderAlert;
import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetOrderDetailService implements GetOrderDetail {

    private final OrderDetails orderDetails;
    private final BuyerEmails buyerEmails;

    GetOrderDetailService(OrderDetails orderDetails, BuyerEmails buyerEmails) {
        this.orderDetails = orderDetails;
        this.buyerEmails = buyerEmails;
    }

    /** The order and its email's status in one read transaction. */
    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        return orderDetails.find(query.orderId())
            .<Result>map(order -> new Result.Found(order,
                OrderAlert.of(order.paidAfterClosing(), buyerEmails.problemOf(query.orderId()))))
            .orElseGet(Result.NotFound::new);
    }
}
