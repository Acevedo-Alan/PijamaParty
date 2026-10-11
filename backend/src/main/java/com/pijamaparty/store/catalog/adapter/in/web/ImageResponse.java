package com.pijamaparty.store.catalog.adapter.in.web;

import com.pijamaparty.store.catalog.application.ProductView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * A photo of a product (UC-06): {@code color} is null for a photo of the whole product, and each file is a WebP at
 * most {@code width} pixels wide, served by Caddy under {@code /uploads/}.
 */
record ImageResponse(UUID id, @Schema(nullable = true) String color, String placeholder, List<FileResponse> files) {

    /** Where Caddy serves the uploads volume (deploy/caddy/Caddyfile). */
    static final String UPLOADS_PATH = "/uploads/";

    static ImageResponse of(ProductView.ImageView view) {
        return new ImageResponse(view.id().value(), view.color().orElse(null), view.placeholder(),
            view.files().stream().map(file -> new FileResponse(file.width(), UPLOADS_PATH + file.fileName())).toList());
    }

    record FileResponse(int width, String url) {}
}
