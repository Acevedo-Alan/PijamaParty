package com.pijamaparty.store.settings.adapter.in.web;

import com.pijamaparty.store.settings.application.GetPublicStoreContact;
import com.pijamaparty.store.settings.domain.ContactEmail;
import com.pijamaparty.store.settings.domain.PlainText;
import com.pijamaparty.store.settings.domain.SocialLink;
import com.pijamaparty.store.settings.domain.StoreSettings;
import com.pijamaparty.store.settings.domain.WhatsAppNumber;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC-36: the contact data, the social links and the announcement of the storefront. Only translates. */
@RestController
@RequestMapping("/api/store")
@Tag(name = "Store")
class StoreContactController {

    private final GetPublicStoreContact getContact;

    StoreContactController(GetPublicStoreContact getContact) {
        this.getContact = getContact;
    }

    @GetMapping("/contact")
    @Operation(summary = "The store's contact data, social links and header announcement",
        description = "Public. A value the store does not have is null, and the store leaves it out. A null "
            + "announcement shows the free-shipping message instead.")
    @ApiResponse(responseCode = "200", description = "The contact data", content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = StoreContactResponse.class)))
    StoreContactResponse contact() {
        return StoreContactResponse.of(getContact.execute());
    }

    /**
     * Every value null when the store has none.
     *
     * @param whatsAppNumber digits only, as wa.me takes it
     */
    record StoreContactResponse(@Schema(nullable = true) String email,
                                @Schema(nullable = true) String whatsAppNumber,
                                @Schema(nullable = true) String openingHours,
                                @Schema(nullable = true) String address,
                                @Schema(nullable = true) String instagramUrl,
                                @Schema(nullable = true) String facebookUrl,
                                @Schema(nullable = true) String tiktokUrl,
                                @Schema(nullable = true) String announcement) {

        static StoreContactResponse of(StoreSettings settings) {
            return new StoreContactResponse(
                settings.email().map(ContactEmail::value).orElse(null),
                settings.whatsAppNumber().map(WhatsAppNumber::digits).orElse(null),
                settings.openingHours().map(PlainText::value).orElse(null),
                settings.address().map(PlainText::value).orElse(null),
                settings.instagram().map(SocialLink::url).orElse(null),
                settings.facebook().map(SocialLink::url).orElse(null),
                settings.tiktok().map(SocialLink::url).orElse(null),
                settings.announcement().map(PlainText::value).orElse(null));
        }
    }
}
