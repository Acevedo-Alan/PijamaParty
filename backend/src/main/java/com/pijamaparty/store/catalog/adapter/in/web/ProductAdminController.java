package com.pijamaparty.store.catalog.adapter.in.web;

import com.pijamaparty.store.catalog.application.CreateProduct;
import com.pijamaparty.store.catalog.application.GenerateVariantMatrix;
import com.pijamaparty.store.catalog.application.GetProductForAdmin;
import com.pijamaparty.store.catalog.application.HideProduct;
import com.pijamaparty.store.catalog.application.ListAllProducts;
import com.pijamaparty.store.catalog.application.ProductDetailsInput;
import com.pijamaparty.store.catalog.application.ProductSummaryView;
import com.pijamaparty.store.catalog.application.ProductView;
import com.pijamaparty.store.catalog.application.PublishProduct;
import com.pijamaparty.store.catalog.application.UpdateProduct;
import com.pijamaparty.store.catalog.application.UpdateVariant;
import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Color;
import com.pijamaparty.store.catalog.domain.Description;
import com.pijamaparty.store.catalog.domain.Material;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.ProductName;
import com.pijamaparty.store.catalog.domain.Season;
import com.pijamaparty.store.catalog.domain.Variant;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.catalog.domain.VariantMatrix;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-06: product and variant management in the back office. Only translates between HTTP and the use cases. */
@RestController
@RequestMapping("/api/admin/catalog")
@Tag(name = "Back office products")
@ApiResponse(responseCode = "403", description = "Not the administrator",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class ProductAdminController {

    /** Bounds the offset; 10,000 pages of 50 are far beyond the catalog of the shop. */
    private static final int MAX_PAGE = 10_000;

    /** The domain {@code Size} (a product size), not the Bean Validation annotation. */
    private static final int MAX_SIZE_LENGTH = com.pijamaparty.store.catalog.domain.Size.MAX_LENGTH;

    /**
     * Request texts and lists are only capped to bound the body, at 4 times the domain limits: the domain counts
     * characters after trimming and answers 422 with the field or problem (UC-06).
     */
    private static final int SLACK = 4;

    private final ListAllProducts listAll;
    private final GetProductForAdmin get;
    private final CreateProduct create;
    private final UpdateProduct update;
    private final GenerateVariantMatrix generateMatrix;
    private final PublishProduct publish;
    private final HideProduct hide;
    private final UpdateVariant updateVariant;

    ProductAdminController(ListAllProducts listAll, GetProductForAdmin get, CreateProduct create, UpdateProduct update,
                           GenerateVariantMatrix generateMatrix, PublishProduct publish, HideProduct hide,
                           UpdateVariant updateVariant) {
        this.listAll = listAll;
        this.get = get;
        this.create = create;
        this.update = update;
        this.generateMatrix = generateMatrix;
        this.publish = publish;
        this.hide = hide;
        this.updateVariant = updateVariant;
    }

    @GetMapping("/products")
    @Operation(summary = "Every product, published or not, newest first", description = "50 per page, from page 0.")
    @ApiResponse(responseCode = "200", description = "One page of products", content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = ProductPageResponse.class)))
    ResponseEntity<?> list(@RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int page) {
        return switch (listAll.execute(new ListAllProducts.Query(actor(), page))) {
            case ListAllProducts.Result.Listed listed -> ResponseEntity.ok(new ProductPageResponse(
                listed.products().stream().map(ProductSummaryResponse::of).toList(), listed.hasMore()));
            case ListAllProducts.Result.Forbidden forbidden -> forbidden();
        };
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "A product with its sizes, colors, variants and photos")
    @ApiResponse(responseCode = "200", description = "The product", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = ProductResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such product",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> get(@PathVariable UUID id) {
        return switch (get.execute(new GetProductForAdmin.Query(actor(), ProductId.of(id)))) {
            case GetProductForAdmin.Result.Found found -> ResponseEntity.ok(ProductResponse.of(found.product()));
            case GetProductForAdmin.Result.NotFound notFound -> productNotFound();
            case GetProductForAdmin.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PostMapping("/products")
    @Operation(summary = "Create a product",
        description = "It starts hidden and without variants; the slug comes from the name. Requires the CSRF token.")
    @ApiResponse(responseCode = "201", description = "Created", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = ProductResponse.class)))
    @ApiResponse(responseCode = "409", description = "Another product already uses the slug of this name; see slug",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Invalid fields (see fields) or unknown category",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> create(@Valid @RequestBody ProductRequest body) {
        return switch (create.execute(new CreateProduct.Command(actor(), body.toInput()))) {
            case CreateProduct.Result.Created created ->
                ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.of(created.product()));
            case CreateProduct.Result.SlugAlreadyUsed used -> slugAlreadyUsed(used.slug());
            case CreateProduct.Result.InvalidDetails invalid -> invalidDetails(invalid.fields());
            case CreateProduct.Result.UnknownCategory unknown -> unknownCategory();
            case CreateProduct.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PutMapping("/products/{id}")
    @Operation(summary = "Edit the details of a product",
        description = "A new name regenerates the slug. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Updated", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = ProductResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such product",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Another product already uses the slug of this name; see slug",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Invalid fields (see fields) or unknown category",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> update(@PathVariable UUID id, @Valid @RequestBody ProductRequest body) {
        return switch (update.execute(new UpdateProduct.Command(actor(), ProductId.of(id), body.toInput()))) {
            case UpdateProduct.Result.Updated updated -> ResponseEntity.ok(ProductResponse.of(updated.product()));
            case UpdateProduct.Result.NotFound notFound -> productNotFound();
            case UpdateProduct.Result.SlugAlreadyUsed used -> slugAlreadyUsed(used.slug());
            case UpdateProduct.Result.InvalidDetails invalid -> invalidDetails(invalid.fields());
            case UpdateProduct.Result.UnknownCategory unknown -> unknownCategory();
            case UpdateProduct.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PostMapping("/products/{id}/variants/generate")
    @Operation(summary = "Generate the variant matrix",
        description = "Creates the missing size × color variants, active and without a price; never deletes one. "
            + "The given sizes and colors come first in the display order. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "The product with its variants",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such product",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Invalid, repeated or too many sizes or colors; see problem",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> generateVariants(@PathVariable UUID id, @Valid @RequestBody MatrixRequest body) {
        var command = new GenerateVariantMatrix.Command(actor(), ProductId.of(id), body.sizes(), body.colors());
        return switch (generateMatrix.execute(command)) {
            case GenerateVariantMatrix.Result.Generated generated ->
                ResponseEntity.ok(ProductResponse.of(generated.product()));
            case GenerateVariantMatrix.Result.NotFound notFound -> productNotFound();
            case GenerateVariantMatrix.Result.InvalidMatrix invalid -> invalidMatrix(invalid.problem());
            case GenerateVariantMatrix.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PostMapping("/products/{id}/publish")
    @Operation(summary = "Publish a product", description = "Needs an active priced variant. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Published", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = ProductResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such product",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "The product has no active priced variant",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> publish(@PathVariable UUID id) {
        return switch (publish.execute(new PublishProduct.Command(actor(), ProductId.of(id)))) {
            case PublishProduct.Result.Published published -> ResponseEntity.ok(ProductResponse.of(published.product()));
            case PublishProduct.Result.NotFound notFound -> productNotFound();
            case PublishProduct.Result.NoActivePricedVariant none -> problem(HttpStatus.CONFLICT,
                "Product cannot be published", "A product needs an active variant with a price to be published.");
            case PublishProduct.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PostMapping("/products/{id}/hide")
    @Operation(summary = "Hide a product", description = "Takes it out of the shop. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "Hidden", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = ProductResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such product",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> hide(@PathVariable UUID id) {
        return switch (hide.execute(new HideProduct.Command(actor(), ProductId.of(id)))) {
            case HideProduct.Result.Hidden hidden -> ResponseEntity.ok(ProductResponse.of(hidden.product()));
            case HideProduct.Result.NotFound notFound -> productNotFound();
            case HideProduct.Result.Forbidden forbidden -> forbidden();
        };
    }

    @PatchMapping("/variants/{id}")
    @Operation(summary = "Set the price of a variant or activate or deactivate it",
        description = "Both fields are optional. The price can change but not be removed. Requires the CSRF token.")
    @ApiResponse(responseCode = "200", description = "The product of the variant",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such variant",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Last active priced variant of a published product",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Price not from 1 to 1,000,000,000 cents",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> updateVariant(@PathVariable UUID id, @Valid @RequestBody VariantRequest body) {
        var command = new UpdateVariant.Command(actor(), VariantId.of(id), Optional.ofNullable(body.priceCents()),
            Optional.ofNullable(body.active()));
        return switch (updateVariant.execute(command)) {
            case UpdateVariant.Result.Updated updated -> ResponseEntity.ok(ProductResponse.of(updated.product()));
            case UpdateVariant.Result.NotFound notFound -> problem(HttpStatus.NOT_FOUND, "Variant not found", null);
            case UpdateVariant.Result.InvalidPrice invalid -> problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Invalid price", "The price must be from 1 to " + Variant.MAX_PRICE.cents() + " cents.");
            case UpdateVariant.Result.LastActivePricedVariant last -> problem(HttpStatus.CONFLICT,
                "Last active priced variant",
                "A published product needs an active variant with a price; hide the product first.");
            case UpdateVariant.Result.Forbidden forbidden -> forbidden();
        };
    }

    private static Actor actor() {
        return CurrentActor.get().orElseThrow();
    }

    private static ResponseEntity<ProblemDetail> slugAlreadyUsed(String slug) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
            "Another product already uses the slug of this name.");
        problem.setTitle("Slug already used");
        problem.setProperty("slug", slug);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    private static ResponseEntity<ProblemDetail> invalidDetails(Set<ProductDetailsInput.Field> fields) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
            "Name: 1 to " + ProductName.MAX_LENGTH + " characters with letters or digits. Description: up to "
                + Description.MAX_LENGTH + ". Material: 1 to " + Material.MAX_LENGTH + ". No control characters.");
        problem.setTitle("Invalid product details");
        problem.setProperty("fields", fields.stream().map(Enum::name).sorted().toList());
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    private static ResponseEntity<ProblemDetail> unknownCategory() {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown category", "The category does not exist.");
    }

    private static ResponseEntity<ProblemDetail> invalidMatrix(VariantMatrix.Problem reason) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
            "1 to " + VariantMatrix.MAX_SIZES + " sizes of up to " + MAX_SIZE_LENGTH + " characters and 1 to "
                + VariantMatrix.MAX_COLORS + " colors of up to " + Color.MAX_LENGTH
                + " characters, without repeats; a product has at most " + VariantMatrix.MAX_SIZES + " sizes and "
                + VariantMatrix.MAX_COLORS + " colors.");
        problem.setTitle("Invalid variant matrix");
        problem.setProperty("problem", reason.name());
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

    /** The details as typed; the use case trims them and checks the rest of the rules. */
    record ProductRequest(
        @NotNull UUID categoryId,
        @NotNull @Size(max = SLACK * ProductName.MAX_LENGTH) String name,
        @NotNull @Size(max = SLACK * Description.MAX_LENGTH) String description,
        @NotNull @Size(max = SLACK * Material.MAX_LENGTH) String material,
        @NotNull Season season,
        @NotNull Audience audience) {

        ProductDetailsInput toInput() {
            return new ProductDetailsInput(CategoryId.of(categoryId), name, description, material, season, audience);
        }
    }

    /** Sizes and colors as typed, in display order; the use case trims them and checks the rest of the rules. */
    record MatrixRequest(
        @NotNull @Size(max = SLACK * VariantMatrix.MAX_SIZES)
        List<@NotNull @Size(max = SLACK * MAX_SIZE_LENGTH) String> sizes,
        @NotNull @Size(max = SLACK * VariantMatrix.MAX_COLORS)
        List<@NotNull @Size(max = SLACK * Color.MAX_LENGTH) String> colors) {
    }

    /** Both optional: a missing field stays as it is. The price is in cents. */
    record VariantRequest(Long priceCents, Boolean active) {}

    record ProductPageResponse(List<ProductSummaryResponse> items, boolean hasMore) {}

    record ProductSummaryResponse(UUID id, UUID categoryId, String name, String slug, boolean published,
                                  int variantCount) {

        static ProductSummaryResponse of(ProductSummaryView view) {
            return new ProductSummaryResponse(view.id().value(), view.categoryId().value(), view.name(), view.slug(),
                view.published(), view.variantCount());
        }
    }

    record ProductResponse(UUID id, UUID categoryId, String name, String slug, String description, String material,
                           Season season, Audience audience, boolean published, List<String> sizes,
                           List<String> colors, List<VariantResponse> variants, List<ImageResponse> images) {

        static ProductResponse of(ProductView view) {
            return new ProductResponse(view.id().value(), view.categoryId().value(), view.name(), view.slug(),
                view.description(), view.material(), view.season(), view.audience(), view.published(), view.sizes(),
                view.colors(), view.variants().stream().map(VariantResponse::of).toList(),
                view.images().stream().map(ImageResponse::of).toList());
        }
    }

    /** {@code priceCents} is null until the administrator sets a price. */
    record VariantResponse(UUID id, String sku, String size, String color,
                           @Schema(nullable = true) Long priceCents, boolean active) {

        static VariantResponse of(ProductView.VariantView view) {
            return new VariantResponse(view.id().value(), view.sku(), view.size(), view.color(),
                view.price().map(Money::cents).orElse(null), view.active());
        }
    }
}
