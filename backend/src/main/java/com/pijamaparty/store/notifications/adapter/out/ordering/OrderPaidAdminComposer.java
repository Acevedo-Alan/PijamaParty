package com.pijamaparty.store.notifications.adapter.out.ordering;

import com.pijamaparty.store.notifications.application.EmailComposer;
import com.pijamaparty.store.notifications.domain.EmailMessage;
import com.pijamaparty.store.notifications.domain.EmailType;
import com.pijamaparty.store.ordering.api.PaidOrders;
import com.pijamaparty.store.ordering.api.PaidOrders.PaidOrder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;

/**
 * UC-17: the notice of a paid order to the administrator ({@code store.notifications.admin-recipient}): the buyer's
 * name, the city and province, the lines and the total. Without a recipient there is no one to tell: {@code Gone}.
 */
class OrderPaidAdminComposer implements EmailComposer {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidAdminComposer.class);

    private final PaidOrders orders;
    private final MessageSource messages;
    private final String recipient;

    OrderPaidAdminComposer(PaidOrders orders, MessageSource messages, String recipient) {
        this.orders = orders;
        this.messages = messages;
        this.recipient = recipient.strip();
    }

    @Override
    public EmailType type() {
        return EmailType.ORDER_PAID_ADMIN;
    }

    @Override
    public Composition compose(UUID reference) {
        if (recipient.isEmpty()) {
            log.warn("No administrator to tell about paid order {} (store.notifications.admin-recipient)",
                reference);
            return new Composition.Gone();
        }
        Optional<PaidOrder> found = orders.find(reference);
        if (found.isEmpty()) {
            return new Composition.Gone();
        }
        PaidOrder order = found.get();
        String number = Long.toString(order.number());
        var writer = new OrderEmailWriter(messages);
        String subject = writer.message("notifications.order-paid.admin.subject", number);
        writer.heading(subject)
            .paragraph(writer.message("notifications.order-paid.admin.intro", number))
            .block(writer.message("notifications.order-paid.admin.buyer"),
                List.of(order.buyerFirstName() + " " + order.buyerLastName()))
            .block(writer.message("notifications.order-paid.admin.destination"),
                List.of(order.address().city() + ", " + writer.province(order.address().province())))
            .summary(order)
            .paragraph(writer.message("notifications.order-paid.admin.back-office"));
        return new Composition.Composed(new EmailMessage(recipient, subject, writer.html(), writer.text()));
    }
}
