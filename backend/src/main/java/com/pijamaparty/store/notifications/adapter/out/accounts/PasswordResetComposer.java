package com.pijamaparty.store.notifications.adapter.out.accounts;

import com.pijamaparty.store.accounts.api.PasswordResetLinks;
import com.pijamaparty.store.accounts.api.PasswordResetLinks.PasswordResetLink;
import com.pijamaparty.store.notifications.adapter.out.mail.EmailWriter;
import com.pijamaparty.store.notifications.application.EmailComposer;
import com.pijamaparty.store.notifications.domain.EmailMessage;
import com.pijamaparty.store.notifications.domain.EmailType;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.MessageSource;

/**
 * UC-19 (T-E4-03): the email that lets a customer set a new password, with a link minted for this email, so its 30
 * minutes count from the sending. An account deleted gets none.
 */
class PasswordResetComposer implements EmailComposer {

    /** The storefront's reset page; the token goes in the fragment, as in the verification link. */
    static final String RESET_PATH = "/cuenta/restablecer#";

    private final PasswordResetLinks links;
    private final MessageSource messages;
    private final String publicUrl;

    PasswordResetComposer(PasswordResetLinks links, MessageSource messages, URI publicUrl) {
        this.links = links;
        this.messages = messages;
        this.publicUrl = publicUrl.toString().replaceAll("/+$", "");
    }

    @Override
    public EmailType type() {
        return EmailType.PASSWORD_RESET;
    }

    @Override
    public Composition compose(UUID reference) {
        Optional<PasswordResetLink> found = links.issue(reference);
        if (found.isEmpty()) {
            return new Composition.Gone();
        }
        PasswordResetLink link = found.get();
        String url = publicUrl + RESET_PATH + link.token().value();
        var writer = new AccountEmailWriter(messages);
        String subject = writer.message("notifications.password-reset.subject");
        writer.heading(writer.message("notifications.password-reset.greeting", EmailWriter.firstName(link.name())))
            .paragraph(writer.message("notifications.password-reset.intro"))
            .link(writer.message("notifications.password-reset.link"),
                writer.message("notifications.password-reset.button"), url)
            .paragraph(writer.message("notifications.password-reset.expiry"))
            .paragraph(writer.message("notifications.password-reset.not-you"));
        return new Composition.Composed(new EmailMessage(link.email(), subject, writer.html(), writer.text()));
    }
}
