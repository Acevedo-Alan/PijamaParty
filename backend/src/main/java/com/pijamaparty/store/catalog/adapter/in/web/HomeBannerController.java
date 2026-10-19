package com.pijamaparty.store.catalog.adapter.in.web;

import com.pijamaparty.store.catalog.application.GetHomeBanner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC-30 and UC-32: the character banner of the home page. Only translates between HTTP and the use case. */
@RestController
@RequestMapping("/api/home/banner")
@Tag(name = "Catalog")
class HomeBannerController {

    private final GetHomeBanner getHomeBanner;

    HomeBannerController(GetHomeBanner getHomeBanner) {
        this.getHomeBanner = getHomeBanner;
    }

    @GetMapping
    @Operation(summary = "The published character banner of the home page",
        description = "linkPath is always a path of this store, starting with a single /.")
    @ApiResponse(responseCode = "200", description = "The published banner",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = HomeBannerResponse.class)))
    @ApiResponse(responseCode = "204", description = "No banner is published: the home page shows no banner section")
    @ApiResponse(responseCode = "429", description = "Over the catalog limit (RNF-22); Retry-After says when to retry",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> banner() {
        return switch (getHomeBanner.execute()) {
            case GetHomeBanner.Result.Published published -> ResponseEntity.ok(HomeBannerResponse.of(published.banner()));
            case GetHomeBanner.Result.NotPublished notPublished -> ResponseEntity.noContent().build();
        };
    }

    record HomeBannerResponse(String title, String body, String linkText, String linkPath,
                              CatalogController.ListingImageResponse image) {

        static HomeBannerResponse of(GetHomeBanner.PublishedBanner banner) {
            return new HomeBannerResponse(banner.title(), banner.body(), banner.linkText(), banner.linkPath(),
                CatalogController.ListingImageResponse.of(banner.image()));
        }
    }
}
