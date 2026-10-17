package com.pijamaparty.store.notifications.adapter.out.accounts;

import com.pijamaparty.store.accounts.api.LinkConfirmationLinks;
import com.pijamaparty.store.accounts.api.LinkConfirmationLinks.LinkConfirmationLink;
import com.pijamaparty.store.notifications.adapter.out.mail.EmailWriter;
import com.pijamaparty.store.notifications.application.EmailComposer;
import com.pijamaparty.store.notifications.domain.EmailMessage;
import com.pijamaparty.store.notifications.domain.EmailType;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.MessageSource;

/**
 * UC-27 (T-E4-14): the email that confirms linking an external account to a customer, with a link minted for this
 * email, so its 24 hours count from the sending. An account deleted, or with no external account waiting, gets none.
 */
class LinkConfirmationComposer implements EmailComposer {

    /** The storefront's confirmation page; the token goes in the fragment, as in the verification link. */
    static final String CONFIRMATION_PATH = "/cuenta/vincular#";

    private final LinkConfirmationLinks links;
    private final MessageSource messages;
    private final String publicUrl;

    LinkConfirmationComposer(LinkConfirmationLinks links, MessageSource messages, URI publicUrl) {
        this.links = links;
        this.messages = messages;
        this.publicUrl = publicUrl.toString().replaceAll("/+$", "");
    }

    @Override
    public EmailType type() {
        return EmailType.LINK_CONFIRMATION;
    }

    @Override
    public Composition compose(UUID reference) {
        Optional<LinkConfirmationLink> found = links.issue(reference);
        if (found.isEmpty()) {
            return new Composition.Gone();
        }
        LinkConfirmationLink link = found.get();
        String url = publicUrl + CONFIRMATION_PATH + link.token().value();
        String provider = providerName(link.provider());
        var writer = new AccountEmailWriter(messages);
        String subject = writer.message("notifications.link-confirmation.subject", provider);
        writer.heading(writer.message("notifications.link-confirmation.greeting", EmailWriter.firstName(link.name())))
            .paragraph(writer.message("notifications.link-confirmation.intro", provider))
            .link(writer.message("notifications.link-confirmation.link"),
                writer.message("notifications.link-confirmation.button", provider), url)
            .paragraph(writer.message("notifications.link-confirmation.expiry"))
            .paragraph(writer.message("notifications.link-confirmation.not-you", provider));
        return new Composition.Composed(new EmailMessage(link.email(), subject, writer.html(), writer.text()));
    }

    /** The provider's brand, as people write it. */
    private static String providerName(LinkConfirmationLinks.Provider provider) {
        return switch (provider) {
            case GOOGLE -> "Google";
            case FACEBOOK -> "Facebook";
        };
    }
}
