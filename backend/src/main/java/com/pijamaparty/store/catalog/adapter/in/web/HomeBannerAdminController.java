package com.pijamaparty.store.catalog.adapter.in.web;

import com.pijamaparty.store.catalog.application.GetHomeBannerForAdmin;
import com.pijamaparty.store.catalog.application.HideHomeBanner;
import com.pijamaparty.store.catalog.application.HomeBannerView;
import com.pijamaparty.store.catalog.application.PublishHomeBanner;
import com.pijamaparty.store.catalog.application.ReplaceHomeBannerPhoto;
import com.pijamaparty.store.catalog.application.UpdateHomeBanner;
import com.pijamaparty.store.catalog.domain.HomeBanner;
import com.pijamaparty.store.catalog.domain.ImageDimensions;
import com.pijamaparty.store.catalog.domain.StorePath;
import com.pijamaparty.store.catalog.domain.UploadKey;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** UC-32: the character banner in the back office ("Portada"). Only translates between HTTP and the use cases. */
@RestController
@RequestMapping("/api/admin/home/banner")
@Tag(name = "Back office home page")
@ApiResponse(responseCode = "403", description = "Not the administrator",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class HomeBannerAdminController {

    /** Request texts are only capped to bound them, at 4 times the domain limits (as for products). */
    private static final int SLACK = 4;

    private final GetHomeBannerForAdmin get;
    private final UpdateHomeBanner update;
    private final PublishHomeBanner publish;
    private final HideHomeBanner hide;
    private final ReplaceHomeBannerPhoto replacePhoto;

    HomeBannerAdminController(GetHomeBannerForAdmin get, UpdateHomeBanner update, PublishHomeBanner publish,
                              HideHomeBanner hide, ReplaceHomeBannerPhoto replacePhoto) {
        this.get = get;
        this.update = update;
        this.publish = publish;
        this.hide = hide;
        this.replacePhoto = replacePhoto;
    }

    @GetMapping
    @Operation(summary = "The banner as stored, published or not",
        description = "The texts are null until first saved, the image until a photo is uploaded.")
    @ApiResponse(responseCode = "200", description = "The banner", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = AdminHomeBannerResponse.class)))
    ResponseEntity<?> get() {
        return switch (get.execute(new GetHomeBannerForAdmin.Query(actor()))) {
            case GetHomeBannerForAdmin.Result.Found found -> ResponseEntity.ok(AdminHomeBannerResponse.of(found.banner()));
            case GetHomeBannerForAdmin.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PutMapping
    @Operation(summary = "Edit the banner's texts",
        description = "Replaces the title (1 to 60 characters), the text (0 to 200), the link text (1 to 30) and the "
            + "link target (a path of this store: one leading /, no backslash, at most 200 characters), all trimmed. "
            + "A published banner stays published. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "The banner with its new texts",
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = AdminHomeBannerResponse.class)))
    @ApiResponse(responseCode = "422", description = "INVALID_BANNER with the invalid fields",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> update(@Valid @RequestBody HomeBannerRequest body) {
        var command = new UpdateHomeBanner.Command(actor(), body.title(), body.body() == null ? "" : body.body(),
            body.linkText(), body.linkPath());
        return switch (update.execute(command)) {
            case UpdateHomeBanner.Result.Updated updated -> ResponseEntity.ok(AdminHomeBannerResponse.of(updated.banner()));
            case UpdateHomeBanner.Result.Invalid invalid -> invalidBanner(invalid.fields());
            case UpdateHomeBanner.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PostMapping("/publish")
    @Operation(summary = "Show the banner on the home page",
        description = "Needs the photo, the title, the link text and the target. Publishing a published banner "
            + "changes nothing. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "The published banner",
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = AdminHomeBannerResponse.class)))
    @ApiResponse(responseCode = "409", description = "INCOMPLETE_BANNER with what is missing; the banner stays hidden",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> publish() {
        return switch (publish.execute(new PublishHomeBanner.Command(actor()))) {
            case PublishHomeBanner.Result.Published published ->
                ResponseEntity.ok(AdminHomeBannerResponse.of(published.banner()));
            case PublishHomeBanner.Result.Incomplete incomplete -> incompleteBanner(incomplete.missing());
            case PublishHomeBanner.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PostMapping("/hide")
    @Operation(summary = "Take the banner off the home page",
        description = "Hiding a hidden banner changes nothing. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "The hidden banner",
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = AdminHomeBannerResponse.class)))
    ResponseEntity<?> hide() {
        return switch (hide.execute(new HideHomeBanner.Command(actor()))) {
            case HideHomeBanner.Result.Hidden hidden -> ResponseEntity.ok(AdminHomeBannerResponse.of(hidden.banner()));
            case HideHomeBanner.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PostMapping(path = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload the banner's photo, replacing the current one",
        description = "JPG, PNG or WebP up to 10 MB, recognized by its content, stored as product photos are: WebP at "
            + "400, 600, 800 and 1200 px wide with a blurred placeholder. The old photo's files are deleted. Retrying with "
            + "the same Idempotency-Key and file returns the banner unchanged. Requires the CSRF token.")
    @ApiResponse(responseCode = "201", description = "Replaced", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = AdminHomeBannerResponse.class)))
    @ApiResponse(responseCode = "200", description = "Already uploaded with this key and file",
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = AdminHomeBannerResponse.class)))
    @ApiResponse(responseCode = "400", description = "Missing or invalid Idempotency-Key, or no file",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "413", description = "Larger than 10 MB",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "See problem: UNSUPPORTED_FORMAT, TOO_LARGE, UNREADABLE or "
        + "KEY_REUSED",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Other photos are being converted; retry with the same "
        + "Idempotency-Key after Retry-After seconds",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> replacePhoto(
        @Parameter(description = "A UUID per photo, repeated on retries") @RequestHeader("Idempotency-Key")
        String idempotencyKey,
        @RequestPart("file") MultipartFile file) {
        if (!ProductImageAdminController.UUID_TEXT.matcher(idempotencyKey).matches()) {
            return problem(HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key", "The Idempotency-Key must be a UUID.");
        }
        var command = new ReplaceHomeBannerPhoto.Command(actor(), UploadKey.of(UUID.fromString(idempotencyKey)),
            bytesOf(file));
        return switch (replacePhoto.execute(command)) {
            case ReplaceHomeBannerPhoto.Result.Replaced replaced ->
                ResponseEntity.status(HttpStatus.CREATED).body(AdminHomeBannerResponse.of(replaced.banner()));
            case ReplaceHomeBannerPhoto.Result.AlreadyUploaded uploaded ->
                ResponseEntity.ok(AdminHomeBannerResponse.of(uploaded.banner()));
            case ReplaceHomeBannerPhoto.Result.UnsupportedFormat unsupported -> invalidPhoto("UNSUPPORTED_FORMAT",
                "The file is not a JPG, PNG or WebP image.");
            case ReplaceHomeBannerPhoto.Result.TooLarge tooLarge -> invalidPhoto("TOO_LARGE",
                "At most " + ImageDimensions.MAX_SIDE + " pixels per side and "
                    + ImageDimensions.MAX_PIXELS / 1_000_000 + " megapixels.");
            case ReplaceHomeBannerPhoto.Result.Unreadable unreadable -> invalidPhoto("UNREADABLE",
                "The image cannot be read.");
            case ReplaceHomeBannerPhoto.Result.KeyReused reused -> invalidPhoto("KEY_REUSED",
                "This Idempotency-Key was used with another file.");
            case ReplaceHomeBannerPhoto.Result.Forbidden forbidden -> forbidden();
        };
    }

    private static byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Actor actor() {
        return CurrentActor.get().orElseThrow();
    }

    private static ResponseEntity<ProblemDetail> invalidBanner(Set<HomeBanner.Field> fields) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
            "Title: 1 to " + HomeBanner.Texts.MAX_TITLE + " characters. Text: at most " + HomeBanner.Texts.MAX_BODY
                + ". Link text: 1 to " + HomeBanner.Texts.MAX_LINK_TEXT + ". Link target: a path of this store "
                + "starting with a single /, without backslashes, of at most " + StorePath.MAX_LENGTH + " characters.");
        problem.setTitle("Invalid banner");
        problem.setProperty("problem", "INVALID_BANNER");
        problem.setProperty("fields", fields.stream()
            .sorted(Comparator.naturalOrder())
            .map(HomeBannerAdminController::fieldName)
            .toList());
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    private static ResponseEntity<ProblemDetail> incompleteBanner(Set<HomeBanner.Part> missing) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
            "Publishing needs the photo, the title, the link text and the link target.");
        problem.setTitle("Incomplete banner");
        problem.setProperty("problem", "INCOMPLETE_BANNER");
        problem.setProperty("missing", missing.stream()
            .sorted(Comparator.naturalOrder())
            .map(HomeBannerAdminController::partName)
            .toList());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    private static String fieldName(HomeBanner.Field field) {
        return switch (field) {
            case TITLE -> "title";
            case BODY -> "body";
            case LINK_TEXT -> "linkText";
            case LINK_PATH -> "linkPath";
        };
    }

    private static String partName(HomeBanner.Part part) {
        return switch (part) {
            case PHOTO -> "photo";
            case TITLE -> "title";
            case LINK_TEXT -> "linkText";
            case LINK_PATH -> "linkPath";
        };
    }

    private static ResponseEntity<ProblemDetail> invalidPhoto(String reason, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, detail);
        problem.setTitle("Invalid photo");
        problem.setProperty("problem", reason);
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    private static ResponseEntity<ProblemDetail> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetail.forStatus(HttpStatus.FORBIDDEN));
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return ResponseEntity.status(status).body(problem);
    }

    /** The texts as typed; the use case trims them and checks their rules. A missing text is an empty one. */
    record HomeBannerRequest(@NotNull @Size(max = SLACK * HomeBanner.Texts.MAX_TITLE) String title,
                             @Size(max = SLACK * HomeBanner.Texts.MAX_BODY) String body,
                             @NotNull @Size(max = SLACK * HomeBanner.Texts.MAX_LINK_TEXT) String linkText,
                             @NotNull @Size(max = SLACK * StorePath.MAX_LENGTH) String linkPath) {}

    record AdminHomeBannerResponse(@Schema(nullable = true) String title, @Schema(nullable = true) String body,
                                   @Schema(nullable = true) String linkText, @Schema(nullable = true) String linkPath,
                                   @Schema(nullable = true) CatalogController.ListingImageResponse image,
                                   boolean published, Instant updatedAt) {

        static AdminHomeBannerResponse of(HomeBannerView banner) {
            var texts = banner.texts();
            return new AdminHomeBannerResponse(texts.map(HomeBannerView.Texts::title).orElse(null),
                texts.map(HomeBannerView.Texts::body).orElse(null),
                texts.map(HomeBannerView.Texts::linkText).orElse(null),
                texts.map(HomeBannerView.Texts::linkPath).orElse(null),
                banner.image().map(CatalogController.ListingImageResponse::of).orElse(null),
                banner.published(), banner.updatedAt());
        }
    }
}
