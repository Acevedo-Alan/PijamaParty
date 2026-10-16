package com.pijamaparty.store.notifications.adapter.out.accounts;

import com.pijamaparty.store.accounts.api.EmailVerificationLinks;
import com.pijamaparty.store.accounts.api.EmailVerificationLinks.EmailVerificationLink;
import com.pijamaparty.store.notifications.adapter.out.mail.EmailWriter;
import com.pijamaparty.store.notifications.application.EmailComposer;
import com.pijamaparty.store.notifications.domain.EmailMessage;
import com.pijamaparty.store.notifications.domain.EmailType;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.MessageSource;

/**
 * UC-19 (T-E4-02): the email that verifies a new customer's address, with a link minted for this email, so its 24
 * hours count from the sending. An account already verified or deleted gets none.
 */
class EmailVerificationComposer implements EmailComposer {

    /**
     * The storefront's verification page. The token goes in the fragment, which the browser never sends: it stays out
     * of server logs, of the Referer and of the page views sent to analytics.
     */
    static final String VERIFICATION_PATH = "/cuenta/verificar#";

    private final EmailVerificationLinks links;
    private final MessageSource messages;
    private final String publicUrl;

    EmailVerificationComposer(EmailVerificationLinks links, MessageSource messages, URI publicUrl) {
        this.links = links;
        this.messages = messages;
        this.publicUrl = publicUrl.toString().replaceAll("/+$", "");
    }

    @Override
    public EmailType type() {
        return EmailType.EMAIL_VERIFICATION;
    }

    @Override
    public Composition compose(UUID reference) {
        Optional<EmailVerificationLink> found = links.issue(reference);
        if (found.isEmpty()) {
            return new Composition.Gone();
        }
        EmailVerificationLink link = found.get();
        String url = publicUrl + VERIFICATION_PATH + link.token().value();
        var writer = new AccountEmailWriter(messages);
        String subject = writer.message("notifications.email-verification.subject");
        writer.heading(writer.message("notifications.email-verification.greeting", EmailWriter.firstName(link.name())))
            .paragraph(writer.message("notifications.email-verification.intro"))
            .link(writer.message("notifications.email-verification.link"),
                writer.message("notifications.email-verification.button"), url)
            .paragraph(writer.message("notifications.email-verification.expiry"))
            .paragraph(writer.message("notifications.email-verification.not-you"));
        return new Composition.Composed(new EmailMessage(link.email(), subject, writer.html(), writer.text()));
    }
}
