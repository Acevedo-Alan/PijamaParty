package com.pijamaparty.store.catalog.adapter.in.web;

import com.pijamaparty.store.catalog.application.FilterOptions;
import com.pijamaparty.store.catalog.application.GetFilterOptions;
import com.pijamaparty.store.catalog.application.GetProductDetail;
import com.pijamaparty.store.catalog.application.ListProducts;
import com.pijamaparty.store.catalog.application.ProductDetail;
import com.pijamaparty.store.catalog.application.ProductListingItem;
import com.pijamaparty.store.catalog.application.ProductView;
import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.ProductFilter;
import com.pijamaparty.store.catalog.domain.ProductSort;
import com.pijamaparty.store.catalog.domain.Season;
import com.pijamaparty.store.catalog.domain.Slug;
import com.pijamaparty.store.platform.domain.Money;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-01 to UC-03: the public catalog. Only translates between HTTP and the use cases. */
@RestController
@RequestMapping("/api/catalog")
@Tag(name = "Catalog")
@ApiResponse(responseCode = "400", description = "Invalid parameters",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "404", description = "No active category has this slug",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
class CatalogController {

    /** Bounds the offset; 1,000 pages of 24 are far beyond the catalog of the shop. */
    private static final int MAX_PAGE = 1_000;

    /** A slug is only capped to bound the request, at 4 times the longest slug. */
    private static final int SLACK = 4;

    private final ListProducts listProducts;
    private final GetFilterOptions getFilterOptions;
    private final GetProductDetail getProductDetail;

    CatalogController(ListProducts listProducts, GetFilterOptions getFilterOptions,
                      GetProductDetail getProductDetail) {
        this.listProducts = listProducts;
        this.getFilterOptions = getFilterOptions;
        this.getProductDetail = getProductDetail;
    }

    @GetMapping("/products")
    @Operation(summary = "Listed products, filtered and sorted",
        description = "24 per page, from page 0. Repeat a filter for several values: they combine with OR, and "
            + "different filters with AND. Size and color apply to one variant with stock; prices are in cents and "
            + "apply to the \"from\" price.")
    @ApiResponse(responseCode = "200", description = "One page of products", content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = ProductListPageResponse.class)))
    @Parameter(name = "size", in = ParameterIn.QUERY, array = @ArraySchema(schema = @Schema(type = "string")))
    @Parameter(name = "color", in = ParameterIn.QUERY, array = @ArraySchema(schema = @Schema(type = "string")))
    @Parameter(name = "material", in = ParameterIn.QUERY, array = @ArraySchema(schema = @Schema(type = "string")))
    ResponseEntity<?> listProducts(
        @RequestParam(required = false) @Size(max = SLACK * Slug.MAX_LENGTH) String category,
        @RequestParam(name = "season", defaultValue = "") Set<Season> seasons,
        @RequestParam(name = "audience", defaultValue = "") Set<Audience> audiences,
        @RequestParam(required = false) Long minPrice,
        @RequestParam(required = false) Long maxPrice,
        @RequestParam(defaultValue = "NEWEST") ProductSort sort,
        @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int page,
        @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parameters) {
        var query = new ListProducts.Query(Optional.ofNullable(category), texts(parameters, "size"),
            texts(parameters, "color"), texts(parameters, "material"), seasons, audiences,
            Optional.ofNullable(minPrice), Optional.ofNullable(maxPrice), sort, page);
        return switch (listProducts.execute(query)) {
            case ListProducts.Result.Listed listed -> ResponseEntity.ok(new ProductListPageResponse(
                listed.products().stream().map(ProductListItemResponse::of).toList(), listed.hasMore()));
            case ListProducts.Result.UnknownCategory unknown -> unknownCategory();
            case ListProducts.Result.InvalidFilter invalid -> problem(HttpStatus.BAD_REQUEST, "Invalid filter",
                "At most " + ProductFilter.MAX_VALUES + " values per filter, sizes, colors and materials as the shop "
                    + "names them, and prices from 0 with the minimum not above the maximum.");
        };
    }

    @GetMapping("/products/{slug}")
    @Operation(summary = "A listed product with its sellable variants",
        description = "Each variant has its price in cents and its available stock. Photos of a color have that color; "
            + "the others are of the whole product.")
    @ApiResponse(responseCode = "200", description = "The product", content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = ProductDetailResponse.class)))
    @ApiResponse(responseCode = "404", description = "No listed product has this slug",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> getProduct(@PathVariable @Size(max = SLACK * Slug.MAX_LENGTH) String slug) {
        return switch (getProductDetail.execute(new GetProductDetail.Query(slug))) {
            case GetProductDetail.Result.Found found -> ResponseEntity.ok(ProductDetailResponse.of(found.product()));
            case GetProductDetail.Result.NotFound notFound -> problem(HttpStatus.NOT_FOUND, "Product not found", null);
        };
    }

    @GetMapping("/filters")
    @Operation(summary = "What the filter panel offers",
        description = "From the listed products of the category, or of the whole shop; the other filters do not "
            + "narrow it. Prices are in cents and null without listed products.")
    @ApiResponse(responseCode = "200", description = "The options", content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = FilterOptionsResponse.class)))
    ResponseEntity<?> getFilterOptions(
        @RequestParam(required = false) @Size(max = SLACK * Slug.MAX_LENGTH) String category) {
        return switch (getFilterOptions.execute(new GetFilterOptions.Query(Optional.ofNullable(category)))) {
            case GetFilterOptions.Result.Found found -> ResponseEntity.ok(FilterOptionsResponse.of(found.options()));
            case GetFilterOptions.Result.UnknownCategory unknown -> unknownCategory();
        };
    }

    /**
     * The values of a free-text filter exactly as sent. Binding them to a {@code List} would split a single value on
     * its commas, and a color may contain one. The use case checks their number and length.
     */
    private static List<String> texts(MultiValueMap<String, String> parameters, String name) {
        return parameters.getOrDefault(name, List.of());
    }

    private static ResponseEntity<ProblemDetail> unknownCategory() {
        return problem(HttpStatus.NOT_FOUND, "Category not found", null);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return ResponseEntity.status(status).body(problem);
    }

    record ProductListPageResponse(List<ProductListItemResponse> items, boolean hasMore) {}

    /** {@code image} is the product's first photo, null when it has none. */
    record ProductListItemResponse(String slug, String name, @Schema(nullable = true) ListingImageResponse image,
                                   long fromPriceCents, boolean soldOut) {

        static ProductListItemResponse of(ProductListingItem item) {
            return new ProductListItemResponse(item.slug(), item.name(),
                item.image().map(ListingImageResponse::of).orElse(null), item.fromPrice().cents(), item.soldOut());
        }
    }

    /** The blur-up placeholder and WebP files at most {@code width} pixels wide, served under {@code /uploads/}. */
    record ListingImageResponse(String placeholder, List<ImageResponse.FileResponse> files) {

        static ListingImageResponse of(ProductListingItem.ListingImage image) {
            return new ListingImageResponse(image.placeholder(), image.files().stream()
                .map(file -> new ImageResponse.FileResponse(file.width(), ImageResponse.UPLOADS_PATH + file.fileName()))
                .toList());
        }
    }

    record ProductDetailResponse(String slug, String name, String description, String material, Season season,
                                 Audience audience, long fromPriceCents, List<String> sizes, List<String> colors,
                                 List<VariantDetailResponse> variants, List<DetailImageResponse> images) {

        static ProductDetailResponse of(ProductDetail product) {
            return new ProductDetailResponse(product.slug(), product.name(), product.description(), product.material(),
                product.season(), product.audience(), product.fromPrice().cents(), product.sizes(), product.colors(),
                product.variants().stream().map(VariantDetailResponse::of).toList(),
                product.images().stream().map(DetailImageResponse::of).toList());
        }
    }

    /** {@code id} is what the cart takes (UC-08); {@code available} is the stock that can be bought now. */
    record VariantDetailResponse(UUID id, String size, String color, long priceCents, int available) {

        static VariantDetailResponse of(ProductDetail.VariantDetail variant) {
            return new VariantDetailResponse(variant.id().value(), variant.size(), variant.color(),
                variant.price().cents(), variant.available());
        }
    }

    /** {@code color} is null for a photo of the whole product. */
    record DetailImageResponse(@Schema(nullable = true) String color, String placeholder,
                               List<ImageResponse.FileResponse> files) {

        static DetailImageResponse of(ProductView.ImageView image) {
            return new DetailImageResponse(image.color().orElse(null), image.placeholder(), image.files().stream()
                .map(file -> new ImageResponse.FileResponse(file.width(), ImageResponse.UPLOADS_PATH + file.fileName()))
                .toList());
        }
    }

    record FilterOptionsResponse(List<String> sizes, List<String> colors, List<String> materials,
                                 List<Season> seasons, List<Audience> audiences,
                                 @Schema(nullable = true) Long minPriceCents,
                                 @Schema(nullable = true) Long maxPriceCents) {

        static FilterOptionsResponse of(FilterOptions options) {
            return new FilterOptionsResponse(options.sizes(), options.colors(), options.materials(), options.seasons(),
                options.audiences(), options.minPrice().map(Money::cents).orElse(null),
                options.maxPrice().map(Money::cents).orElse(null));
        }
    }
}
