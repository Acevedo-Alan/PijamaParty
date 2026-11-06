package com.pijamaparty.store.settings.adapter.in.web;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.settings.application.GetStoreSettings;
import com.pijamaparty.store.settings.application.UpdateStoreSettings;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Comparator;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC-36: "Datos de la tienda" in the back office. Only translates between HTTP and the use cases. */
@RestController
@RequestMapping("/api/admin/store/settings")
@Tag(name = "Back office store settings")
@ApiResponse(responseCode = "403", description = "Not the administrator",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class StoreSettingsAdminController {

    private final GetStoreSettings get;
    private final UpdateStoreSettings update;

    StoreSettingsAdminController(GetStoreSettings get, UpdateStoreSettings update) {
        this.get = get;
        this.update = update;
    }

    @GetMapping
    @Operation(summary = "Every setting of the store and its version")
    @ApiResponse(responseCode = "200", description = "The settings", content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = StoreSettingsResponse.class)))
    ResponseEntity<?> get() {
        return switch (get.execute(new GetStoreSettings.Query(actor()))) {
            case GetStoreSettings.Result.Found found -> ResponseEntity.ok(StoreSettingsResponse.of(found.settings()));
            case GetStoreSettings.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PutMapping
    @Operation(summary = "Save every setting of the store",
        description = "Replaces every value; null or blank is none. Email: at most 254 characters, stored in lower "
            + "case. WhatsApp: 10 to 15 digits once spaces, + and dashes are removed. Opening hours: at most 80 "
            + "characters; address: at most 120; announcement: at most 80; each plain text on one line, trimmed. "
            + "Instagram, Facebook and TikTok: https links of at most 200 characters to the network's own host, with a "
            + "path, without user info or port. Low-stock threshold: 0 to 1,000. version is the one read; a save in "
            + "between is a 409. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Saved, with the new version", content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = StoreSettingsResponse.class)))
    @ApiResponse(responseCode = "409", description = "SETTINGS_CHANGED: another save got in first; nothing is saved",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "INVALID_SETTINGS with the invalid fields; nothing is saved",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> update(@Valid @RequestBody StoreSettingsRequest body) {
        var command = new UpdateStoreSettings.Command(actor(), body.version(), body.typed());
        return switch (update.execute(command)) {
            case UpdateStoreSettings.Result.Updated updated ->
                ResponseEntity.ok(StoreSettingsResponse.of(updated.settings()));
            case UpdateStoreSettings.Result.Invalid invalid -> invalidSettings(invalid.fields());
            case UpdateStoreSettings.Result.VersionChanged changed -> settingsChanged();
            case UpdateStoreSettings.Result.Forbidden forbidden -> forbidden();
        };
    }

    private static Actor actor() {
        return CurrentActor.get().orElseThrow();
    }

    private static ResponseEntity<ProblemDetail> invalidSettings(Set<StoreSettings.Field> fields) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
            "Some values break their rules; see fields.");
        problem.setTitle("Invalid store settings");
        problem.setProperty("problem", "INVALID_SETTINGS");
        problem.setProperty("fields", fields.stream()
            .sorted(Comparator.naturalOrder())
            .map(StoreSettingsAdminController::fieldName)
            .toList());
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    private static ResponseEntity<ProblemDetail> settingsChanged() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
            "The settings were saved from another page after these were read; read them again.");
        problem.setTitle("Store settings changed");
        problem.setProperty("problem", "SETTINGS_CHANGED");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    private static ResponseEntity<ProblemDetail> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetail.forStatus(HttpStatus.FORBIDDEN));
    }

    /** The name of each field, as in the request and the response. */
    private static String fieldName(StoreSettings.Field field) {
        return switch (field) {
            case EMAIL -> "email";
            case WHATSAPP_NUMBER -> "whatsAppNumber";
            case OPENING_HOURS -> "openingHours";
            case ADDRESS -> "address";
            case INSTAGRAM_URL -> "instagramUrl";
            case FACEBOOK_URL -> "facebookUrl";
            case TIKTOK_URL -> "tiktokUrl";
            case ANNOUNCEMENT -> "announcement";
            case LOW_STOCK_THRESHOLD -> "lowStockThreshold";
        };
    }

    /**
     * Every value as typed; null or blank is none. The use case checks the rules and names each field it refuses
     * (422); {@link #MAX_TYPED} only bounds what a request may carry before that (RNF-28, T-E8-14), well above them.
     *
     * @param lowStockThreshold null is refused with the field named
     * @param version the version read with the settings
     */
    record StoreSettingsRequest(@Schema(nullable = true) @Size(max = MAX_TYPED) String email,
                                @Schema(nullable = true) @Size(max = MAX_TYPED) String whatsAppNumber,
                                @Schema(nullable = true) @Size(max = MAX_TYPED) String openingHours,
                                @Schema(nullable = true) @Size(max = MAX_TYPED) String address,
                                @Schema(nullable = true) @Size(max = MAX_TYPED) String instagramUrl,
                                @Schema(nullable = true) @Size(max = MAX_TYPED) String facebookUrl,
                                @Schema(nullable = true) @Size(max = MAX_TYPED) String tiktokUrl,
                                @Schema(nullable = true) @Size(max = MAX_TYPED) String announcement,
                                @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long lowStockThreshold,
                                @NotNull Long version) {

        /** Far above every limit of UC-36 (254 characters at most), so the use case still names the field. */
        static final int MAX_TYPED = 1_000;

        StoreSettings.Typed typed() {
            return new StoreSettings.Typed(email, whatsAppNumber, openingHours, address, instagramUrl, facebookUrl,
                tiktokUrl, announcement, lowStockThreshold);
        }
    }

    /** Every value null when the store has none. */
    record StoreSettingsResponse(@Schema(nullable = true) String email,
                                 @Schema(nullable = true) String whatsAppNumber,
                                 @Schema(nullable = true) String openingHours,
                                 @Schema(nullable = true) String address,
                                 @Schema(nullable = true) String instagramUrl,
                                 @Schema(nullable = true) String facebookUrl,
                                 @Schema(nullable = true) String tiktokUrl,
                                 @Schema(nullable = true) String announcement,
                                 int lowStockThreshold,
                                 long version) {

        static StoreSettingsResponse of(StoreSettings settings) {
            return new StoreSettingsResponse(
                settings.email().map(ContactEmail::value).orElse(null),
                settings.whatsAppNumber().map(WhatsAppNumber::digits).orElse(null),
                settings.openingHours().map(PlainText::value).orElse(null),
                settings.address().map(PlainText::value).orElse(null),
                settings.instagram().map(SocialLink::url).orElse(null),
                settings.facebook().map(SocialLink::url).orElse(null),
                settings.tiktok().map(SocialLink::url).orElse(null),
                settings.announcement().map(PlainText::value).orElse(null),
                settings.lowStockThreshold().units(),
                settings.version());
        }
    }
}
