package com.pijamaparty.store.catalog.adapter.in.web;

import com.pijamaparty.store.catalog.application.ListSitemapPages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * T-E6-23 (RNF-31.1): the sitemap of the storefront (sitemaps.org 0.9), which Caddy serves at {@code /sitemap.xml}:
 * the home page, the FAQ and the help pages (T-E7-28), the active categories and the listed products, as absolute
 * URLs of {@code store.public-url}.
 */
@RestController
@RequestMapping("/api/catalog")
@Tag(name = "Catalog")
class SitemapController {

    /** The storefront's pages outside the catalog that search engines may index. */
    static final List<String> FIXED_PATHS = List.of("/", "/preguntas-frecuentes", "/como-comprar",
        "/cambios-y-devoluciones", "/terminos");

    private static final MediaType XML = new MediaType(MediaType.APPLICATION_XML, StandardCharsets.UTF_8);

    private final ListSitemapPages listSitemapPages;
    private final String storefront;

    SitemapController(ListSitemapPages listSitemapPages, @Value("${store.public-url}") URI publicUrl) {
        this.listSitemapPages = listSitemapPages;
        this.storefront = publicUrl.toString().replaceAll("/+$", "");
    }

    @GetMapping(value = "/sitemap", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "The storefront's sitemap", description = "The home page, the FAQ and help pages, the active categories and "
        + "the listed products. Caddy serves it at /sitemap.xml.")
    @ApiResponse(responseCode = "200", description = "The sitemap",
        content = @Content(mediaType = "application/xml", schema = @Schema(type = "string")))
    ResponseEntity<String> sitemap() {
        return switch (listSitemapPages.execute()) {
            case ListSitemapPages.Result.Listed listed -> ResponseEntity.ok().contentType(XML)
                .cacheControl(CacheControl.noCache()).body(xml(paths(listed)));
        };
    }

    private static List<String> paths(ListSitemapPages.Result.Listed listed) {
        List<String> paths = new ArrayList<>(FIXED_PATHS);
        listed.categorySlugs().forEach(slug -> paths.add("/categoria/" + slug));
        listed.productSlugs().forEach(slug -> paths.add("/producto/" + slug));
        return paths;
    }

    private String xml(List<String> paths) {
        var xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        paths.forEach(path -> xml.append("<url><loc>").append(escape(storefront + path)).append("</loc></url>\n"));
        return xml.append("</urlset>\n").toString();
    }

    /** The five characters XML reserves; slugs never have them, the configured origin might. */
    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
