package com.pijamaparty.store.notifications.adapter.out.ordering;

import com.pijamaparty.store.notifications.application.EmailComposer;
import com.pijamaparty.store.notifications.domain.EmailMessage;
import com.pijamaparty.store.notifications.domain.EmailType;
import com.pijamaparty.store.ordering.api.WithdrawalNotices;
import com.pijamaparty.store.ordering.api.WithdrawalNotices.WithdrawalNotice;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;

/**
 * UC-29 (T-E3-25): the notice of a withdrawal request to the administrator ({@code store.notifications.admin-recipient}):
 * the order number, the procedure code, whether it came out of term and what to do. Without a recipient there is no
 * one to tell: {@code Gone}.
 */
class WithdrawalAdminComposer implements EmailComposer {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalAdminComposer.class);

    private final WithdrawalNotices notices;
    private final MessageSource messages;
    private final String recipient;

    WithdrawalAdminComposer(WithdrawalNotices notices, MessageSource messages, String recipient) {
        this.notices = notices;
        this.messages = messages;
        this.recipient = recipient.strip();
    }

    @Override
    public EmailType type() {
        return EmailType.WITHDRAWAL_RECEIVED_ADMIN;
    }

    @Override
    public Composition compose(UUID reference) {
        if (recipient.isEmpty()) {
            log.warn("No administrator to tell about withdrawal request {} (store.notifications.admin-recipient)",
                reference);
            return new Composition.Gone();
        }
        Optional<WithdrawalNotice> found = notices.find(reference);
        if (found.isEmpty()) {
            return new Composition.Gone();
        }
        WithdrawalNotice notice = found.get();
        String number = Long.toString(notice.orderNumber());
        var writer = new OrderEmailWriter(messages);
        String subject = writer.message("notifications.withdrawal.admin.subject", number, notice.code());
        writer.heading(subject)
            .paragraph(writer.message("notifications.withdrawal.admin.intro", number, notice.code()));
        if (notice.outOfTerm()) {
            writer.paragraph(writer.message("notifications.withdrawal.admin.out-of-term"));
        }
        writer.paragraph(writer.message("notifications.withdrawal.admin.next"));
        return new Composition.Composed(new EmailMessage(recipient, subject, writer.html(), writer.text()));
    }
}
