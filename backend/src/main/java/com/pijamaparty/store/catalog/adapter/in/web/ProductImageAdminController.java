package com.pijamaparty.store.catalog.adapter.in.web;

import com.pijamaparty.store.catalog.application.DeleteProductImage;
import com.pijamaparty.store.catalog.application.ReorderProductImages;
import com.pijamaparty.store.catalog.application.UploadProductImage;
import com.pijamaparty.store.catalog.domain.Color;
import com.pijamaparty.store.catalog.domain.ImageDimensions;
import com.pijamaparty.store.catalog.domain.ImageId;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductId;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** UC-06: the photos of a product in the back office. Only translates between HTTP and the use cases. */
@RestController
@RequestMapping("/api/admin/catalog/products/{id}/images")
@Tag(name = "Back office products")
@ApiResponse(responseCode = "403", description = "Not the administrator",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class ProductImageAdminController {

    /** The canonical text of a UUID, as the frontend's crypto.randomUUID() writes it. */
    private static final Pattern UUID_TEXT =
        Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** Request texts and lists are only capped to bound them, at 4 times the domain limits (as for products). */
    private static final int SLACK = 4;

    private final UploadProductImage upload;
    private final ReorderProductImages reorder;
    private final DeleteProductImage delete;

    ProductImageAdminController(UploadProductImage upload, ReorderProductImages reorder, DeleteProductImage delete) {
        this.upload = upload;
        this.reorder = reorder;
        this.delete = delete;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a photo of a product",
        description = "JPG, PNG or WebP up to 10 MB, recognized by its content. It is stored as WebP at 400, 800 and "
            + "1200 px wide with a blurred placeholder, and goes last. color is optional: one of the product's colors. "
            + "Retrying with the same Idempotency-Key and file returns the stored photo. Requires the CSRF token.")
    @ApiResponse(responseCode = "201", description = "Uploaded", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = ImageResponse.class)))
    @ApiResponse(responseCode = "200", description = "Already uploaded with this key and file",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ImageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Missing or invalid Idempotency-Key, or no file",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such product",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "The product already has 8 photos",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "413", description = "Larger than 10 MB",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "See problem: UNSUPPORTED_FORMAT, TOO_LARGE, UNREADABLE, "
        + "UNKNOWN_COLOR or KEY_REUSED",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Other photos are being converted; retry with the same "
        + "Idempotency-Key after Retry-After seconds",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> upload(
        @PathVariable UUID id,
        @Parameter(description = "A UUID per photo, repeated on retries") @RequestHeader("Idempotency-Key")
        String idempotencyKey,
        @RequestPart("file") MultipartFile file,
        @RequestParam(name = "color", required = false) @Size(max = SLACK * Color.MAX_LENGTH) String color) {
        if (!UUID_TEXT.matcher(idempotencyKey).matches()) {
            return problem(HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key", "The Idempotency-Key must be a UUID.");
        }
        var command = new UploadProductImage.Command(actor(), ProductId.of(id),
            UploadKey.of(UUID.fromString(idempotencyKey)), Optional.ofNullable(color), bytesOf(file));
        return switch (upload.execute(command)) {
            case UploadProductImage.Result.Uploaded uploaded ->
                ResponseEntity.status(HttpStatus.CREATED).body(ImageResponse.of(uploaded.image()));
            case UploadProductImage.Result.AlreadyUploaded uploaded -> ResponseEntity.ok(ImageResponse.of(uploaded.image()));
            case UploadProductImage.Result.NotFound notFound -> productNotFound();
            case UploadProductImage.Result.TooManyImages tooMany -> problem(HttpStatus.CONFLICT, "Too many photos",
                "A product has at most " + Product.MAX_IMAGES + " photos.");
            case UploadProductImage.Result.UnsupportedFormat unsupported -> invalidImage("UNSUPPORTED_FORMAT",
                "The file is not a JPG, PNG or WebP image.");
            case UploadProductImage.Result.TooLarge tooLarge -> invalidImage("TOO_LARGE",
                "At most " + ImageDimensions.MAX_SIDE + " pixels per side and "
                    + ImageDimensions.MAX_PIXELS / 1_000_000 + " megapixels.");
            case UploadProductImage.Result.Unreadable unreadable -> invalidImage("UNREADABLE",
                "The image cannot be read.");
            case UploadProductImage.Result.UnknownColor unknown -> invalidImage("UNKNOWN_COLOR",
                "The product has no variant of that color.");
            case UploadProductImage.Result.KeyReused reused -> invalidImage("KEY_REUSED",
                "This Idempotency-Key was used with another file.");
            case UploadProductImage.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PutMapping("/order")
    @Operation(summary = "Reorder the photos of a product",
        description = "Takes every photo of the product exactly once, in the new order. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "The product with its photos in the new order",
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = ProductAdminController.ProductResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such product",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "The order does not name every photo exactly once",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> reorder(@PathVariable UUID id, @Valid @RequestBody ImageOrderRequest body) {
        var command = new ReorderProductImages.Command(actor(), ProductId.of(id),
            body.imageIds().stream().map(ImageId::of).toList());
        return switch (reorder.execute(command)) {
            case ReorderProductImages.Result.Reordered reordered ->
                ResponseEntity.ok(ProductAdminController.ProductResponse.of(reordered.product()));
            case ReorderProductImages.Result.NotFound notFound -> productNotFound();
            case ReorderProductImages.Result.InvalidOrder invalid -> problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Invalid order", "Send every photo of the product exactly once.");
            case ReorderProductImages.Result.Forbidden forbidden -> forbidden();
        };
    }

    @DeleteMapping("/{imageId}")
    @Operation(summary = "Delete a photo of a product",
        description = "The other photos close the gap and its files are removed. Requires the CSRF token.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No such product or photo",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> delete(@PathVariable UUID id, @PathVariable UUID imageId) {
        return switch (delete.execute(new DeleteProductImage.Command(actor(), ProductId.of(id), ImageId.of(imageId)))) {
            case DeleteProductImage.Result.Deleted deleted -> ResponseEntity.noContent().build();
            case DeleteProductImage.Result.NotFound notFound -> problem(HttpStatus.NOT_FOUND, "Photo not found", null);
            case DeleteProductImage.Result.Forbidden forbidden -> forbidden();
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

    private static ResponseEntity<ProblemDetail> invalidImage(String reason, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, detail);
        problem.setTitle("Invalid photo");
        problem.setProperty("problem", reason);
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    private static ResponseEntity<ProblemDetail> productNotFound() {
        return problem(HttpStatus.NOT_FOUND, "Product not found", null);
    }

    private static ResponseEntity<ProblemDetail> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetail.forStatus(HttpStatus.FORBIDDEN));
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return ResponseEntity.status(status).body(problem);
    }

    /** Every photo id of the product, in the new order. */
    record ImageOrderRequest(@NotNull @Size(max = SLACK * Product.MAX_IMAGES) List<@NotNull UUID> imageIds) {}
}
