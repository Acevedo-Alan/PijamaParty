package com.pijamaparty.store.ordering.adapter.in.web;

import com.pijamaparty.store.ordering.application.LinePhotos;
import java.util.List;

/**
 * The thumbnail of an order item (UC-14, UC-22, E7): the photo the catalog shows now of its variant, as in the
 * catalog (UC-06). Each file is a WebP at most {@code width} pixels wide.
 */
record OrderItemImageResponse(String placeholder, List<OrderItemImageFileResponse> files) {

    /** Where Caddy serves the uploads volume (deploy/caddy/Caddyfile), as the catalog's photos. */
    private static final String UPLOADS_PATH = "/uploads/";

    static OrderItemImageResponse of(LinePhotos.Photo photo) {
        return new OrderItemImageResponse(photo.placeholder(), photo.files().stream()
            .map(file -> new OrderItemImageFileResponse(file.width(), UPLOADS_PATH + file.fileName()))
            .toList());
    }

    record OrderItemImageFileResponse(int width, String url) {}
}
