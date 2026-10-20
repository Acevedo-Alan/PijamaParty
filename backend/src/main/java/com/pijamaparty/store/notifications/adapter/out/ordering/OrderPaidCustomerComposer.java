package com.pijamaparty.store.notifications.adapter.out.ordering;

import com.pijamaparty.store.notifications.application.EmailComposer;
import com.pijamaparty.store.notifications.domain.EmailMessage;
import com.pijamaparty.store.notifications.domain.EmailType;
import com.pijamaparty.store.ordering.api.PaidOrders;
import com.pijamaparty.store.ordering.api.PaidOrders.PaidOrder;
import com.pijamaparty.store.ordering.api.TrackingLinks;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.MessageSource;

/**
 * UC-17: the payment-confirmed email to the buyer, at the order's email: the summary, the total, the shipping address
 * and a tracking link minted for this email (T-E3-09), which also tells that the shipment tracking number will show
 * there.
 */
class OrderPaidCustomerComposer implements EmailComposer {

    /** The storefront's tracking page (UC-14). */
    static final String TRACKING_PATH = "/pedido/";

    private final PaidOrders orders;
    private final TrackingLinks trackingLinks;
    private final MessageSource messages;
    private final String publicUrl;

    OrderPaidCustomerComposer(PaidOrders orders, TrackingLinks trackingLinks, MessageSource messages, URI publicUrl) {
        this.orders = orders;
        this.trackingLinks = trackingLinks;
        this.messages = messages;
        this.publicUrl = publicUrl.toString().replaceAll("/+$", "");
    }

    @Override
    public EmailType type() {
        return EmailType.ORDER_PAID_CUSTOMER;
    }

    /** The link is minted only for an order that exists, right before the email is sent. */
    @Override
    public Composition compose(UUID reference) {
        Optional<PaidOrder> found = orders.find(reference);
        if (found.isEmpty()) {
            return new Composition.Gone();
        }
        PaidOrder order = found.get();
        String link = publicUrl + TRACKING_PATH + trackingLinks.issue(order.orderId()).value();
        String number = Long.toString(order.number());
        var writer = new OrderEmailWriter(messages);
        String subject = writer.message("notifications.order-paid.customer.subject", number);
        writer.heading(writer.message("notifications.order-paid.customer.greeting", number, order.buyerFirstName()))
            .paragraph(writer.message("notifications.order-paid.customer.intro", number))
            .summary(order)
            .block(writer.message("notifications.order-paid.customer.address"), writer.address(order.address()))
            .link(writer.message("notifications.order-paid.customer.tracking"),
                writer.message("notifications.order-paid.customer.tracking-button"), link)
            .paragraph(writer.message("notifications.order-paid.customer.reply"));
        return new Composition.Composed(new EmailMessage(order.buyerEmail(), subject, writer.html(), writer.text()));
    }
}
