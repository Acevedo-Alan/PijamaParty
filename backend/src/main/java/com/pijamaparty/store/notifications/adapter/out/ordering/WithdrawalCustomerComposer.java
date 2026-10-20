package com.pijamaparty.store.notifications.adapter.out.ordering;

import com.pijamaparty.store.notifications.application.EmailComposer;
import com.pijamaparty.store.notifications.domain.EmailMessage;
import com.pijamaparty.store.notifications.domain.EmailType;
import com.pijamaparty.store.ordering.api.WithdrawalNotices;
import com.pijamaparty.store.ordering.api.WithdrawalNotices.WithdrawalNotice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.MessageSource;

/**
 * UC-29 (T-E3-25): the procedure code of a withdrawal request, to the order's email as it is when sent, with what
 * comes next. The text is a placeholder, to be validated with a professional.
 */
class WithdrawalCustomerComposer implements EmailComposer {

    private final WithdrawalNotices notices;
    private final MessageSource messages;

    WithdrawalCustomerComposer(WithdrawalNotices notices, MessageSource messages) {
        this.notices = notices;
        this.messages = messages;
    }

    @Override
    public EmailType type() {
        return EmailType.WITHDRAWAL_RECEIVED_CUSTOMER;
    }

    @Override
    public Composition compose(UUID reference) {
        Optional<WithdrawalNotice> found = notices.find(reference);
        if (found.isEmpty()) {
            return new Composition.Gone();
        }
        WithdrawalNotice notice = found.get();
        String number = Long.toString(notice.orderNumber());
        var writer = new OrderEmailWriter(messages);
        String subject = writer.message("notifications.withdrawal.customer.subject", number);
        writer.heading(writer.message("notifications.withdrawal.customer.greeting", number, notice.buyerFirstName()))
            .paragraph(writer.message("notifications.withdrawal.customer.intro", number))
            .block(writer.message("notifications.withdrawal.customer.code"), List.of(notice.code()))
            .paragraph(writer.message("notifications.withdrawal.customer.next"))
            .paragraph(writer.message("notifications.withdrawal.customer.reply"));
        return new Composition.Composed(new EmailMessage(notice.buyerEmail(), subject, writer.html(), writer.text()));
    }
}
