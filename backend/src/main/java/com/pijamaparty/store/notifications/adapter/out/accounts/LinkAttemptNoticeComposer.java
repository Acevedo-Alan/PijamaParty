package com.pijamaparty.store.notifications.adapter.out.accounts;

import com.pijamaparty.store.accounts.api.LinkAttemptNotices;
import com.pijamaparty.store.accounts.api.LinkAttemptNotices.Recipient;
import com.pijamaparty.store.notifications.adapter.out.mail.EmailWriter;
import com.pijamaparty.store.notifications.application.EmailComposer;
import com.pijamaparty.store.notifications.domain.EmailMessage;
import com.pijamaparty.store.notifications.domain.EmailType;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.MessageSource;

/**
 * UC-27 (T-E4-14): tells a customer that someone typed its email to connect a Facebook account (only Facebook comes
 * without email), that nothing was linked, and that it can sign in and link it from "Mi cuenta". It carries no token:
 * the link goes to the sign-in page. An account deleted, or that is not a customer's, gets none.
 */
class LinkAttemptNoticeComposer implements EmailComposer {

    static final String SIGN_IN_PATH = "/cuenta/ingresar";

    private final LinkAttemptNotices notices;
    private final MessageSource messages;
    private final String publicUrl;

    LinkAttemptNoticeComposer(LinkAttemptNotices notices, MessageSource messages, URI publicUrl) {
        this.notices = notices;
        this.messages = messages;
        this.publicUrl = publicUrl.toString().replaceAll("/+$", "");
    }

    @Override
    public EmailType type() {
        return EmailType.LINK_ATTEMPT_NOTICE;
    }

    @Override
    public Composition compose(UUID reference) {
        Optional<Recipient> found = notices.recipientOf(reference);
        if (found.isEmpty()) {
            return new Composition.Gone();
        }
        Recipient recipient = found.get();
        var writer = new AccountEmailWriter(messages);
        String subject = writer.message("notifications.link-attempt.subject");
        writer.heading(writer.message("notifications.link-attempt.greeting", EmailWriter.firstName(recipient.name())))
            .paragraph(writer.message("notifications.link-attempt.intro"))
            .link(writer.message("notifications.link-attempt.link"),
                writer.message("notifications.link-attempt.button"), publicUrl + SIGN_IN_PATH)
            .paragraph(writer.message("notifications.link-attempt.not-you"));
        return new Composition.Composed(new EmailMessage(recipient.email(), subject, writer.html(), writer.text()));
    }
}
