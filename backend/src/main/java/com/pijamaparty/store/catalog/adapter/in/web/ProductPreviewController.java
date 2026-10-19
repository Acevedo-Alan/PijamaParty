package com.pijamaparty.store.catalog.adapter.in.web;

import com.pijamaparty.store.catalog.application.GetProductDetail;
import com.pijamaparty.store.catalog.application.ProductDetail;
import com.pijamaparty.store.catalog.application.ProductView;
import com.pijamaparty.store.catalog.domain.Slug;
import com.pijamaparty.store.platform.domain.Money;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * T-E6-07 (RNF-31.2): the page a link-preview bot (WhatsApp, Facebook, Instagram, X, Telegram) reads for a shared
 * product, since bots do not run the storefront's JavaScript. Caddy sends only those bots here, by user agent. Reads
 * the product through UC-03, so a preview shows exactly what a visitor can see; this class only writes it as HTML.
 */
@RestController
@RequestMapping("/api/catalog/products")
@Tag(name = "Catalog")
class ProductPreviewController {

    /** The longest description in a preview; longer ones are cut at a word. */
    static final int DESCRIPTION_LENGTH = 200;

    /** The photo file a preview shows: Facebook asks for at least 1,200 px wide. */
    static final int IMAGE_WIDTH = 1200;

    private static final Locale CUSTOMER_LOCALE = Locale.forLanguageTag("es-AR");
    private static final MediaType HTML = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final int SLACK = 4;

    private final GetProductDetail getProductDetail;
    private final MessageSource messages;
    private final String storefront;

    ProductPreviewController(GetProductDetail getProductDetail, MessageSource messages,
                             @Value("${store.public-url}") URI publicUrl) {
        this.getProductDetail = getProductDetail;
        this.messages = messages;
        this.storefront = publicUrl.toString().replaceAll("/+$", "");
    }

    @GetMapping(value = "/{slug}/preview", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "The link preview of a listed product, for the bots of WhatsApp, Facebook, Instagram, X and "
        + "Telegram", description = "A minimal HTML page with the Open Graph tags: name, price, description, "
        + "canonical URL and first photo. Caddy sends those bots here from /producto/{slug}.")
    @ApiResponse(responseCode = "200", description = "The preview",
        content = @Content(mediaType = "text/html", schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "404", description = "No listed product has this slug: the store's own preview",
        content = @Content(mediaType = "text/html", schema = @Schema(type = "string")))
    ResponseEntity<String> preview(@PathVariable @Size(max = SLACK * Slug.MAX_LENGTH) String slug) {
        return switch (getProductDetail.execute(new GetProductDetail.Query(slug))) {
            case GetProductDetail.Result.Found found -> html(HttpStatus.OK, product(found.product()));
            case GetProductDetail.Result.NotFound notFound -> html(HttpStatus.NOT_FOUND, store());
        };
    }

    private String product(ProductDetail product) {
        String url = storefront + "/producto/" + product.slug();
        String title = message("catalog.preview.title", product.name());
        String price = price(product);
        String summary = description(product.description())
            .map(description -> message("catalog.preview.description", price, description))
            .orElse(price);
        Optional<String> image = product.images().stream().findFirst().map(this::imageUrl);

        var page = new StringBuilder();
        page.append("<!doctype html>\n<html lang=\"es-AR\">\n<head>\n<meta charset=\"utf-8\">\n");
        page.append("<title>").append(escape(title)).append("</title>\n");
        meta(page, "name", "description", summary);
        page.append("<link rel=\"canonical\" href=\"").append(escape(url)).append("\">\n");
        meta(page, "property", "og:site_name", message("catalog.preview.site-name"));
        meta(page, "property", "og:locale", "es_AR");
        meta(page, "property", "og:type", "product");
        meta(page, "property", "og:title", title);
        meta(page, "property", "og:description", summary);
        meta(page, "property", "og:url", url);
        image.ifPresent(src -> {
            meta(page, "property", "og:image", src);
            meta(page, "property", "og:image:alt", product.name());
        });
        meta(page, "property", "product:price:amount", amount(product.fromPrice()));
        meta(page, "property", "product:price:currency", "ARS");
        meta(page, "name", "twitter:card", image.isPresent() ? "summary_large_image" : "summary");
        page.append("</head>\n<body>\n<h1>").append(escape(product.name())).append("</h1>\n");
        page.append("<p>").append(escape(price)).append("</p>\n");
        page.append("<p><a href=\"").append(escape(url)).append("\">").append(escape(message("catalog.preview.link")))
            .append("</a></p>\n</body>\n</html>\n");
        return page.toString();
    }

    private String store() {
        String name = message("catalog.preview.site-name");
        String home = storefront + "/";
        var page = new StringBuilder();
        page.append("<!doctype html>\n<html lang=\"es-AR\">\n<head>\n<meta charset=\"utf-8\">\n");
        page.append("<title>").append(escape(name)).append("</title>\n");
        meta(page, "property", "og:site_name", name);
        meta(page, "property", "og:locale", "es_AR");
        meta(page, "property", "og:type", "website");
        meta(page, "property", "og:title", name);
        meta(page, "property", "og:url", home);
        meta(page, "name", "twitter:card", "summary");
        page.append("</head>\n<body>\n<p><a href=\"").append(escape(home)).append("\">").append(escape(name))
            .append("</a></p>\n</body>\n</html>\n");
        return page.toString();
    }

    /** The lowest price, with "Desde" when the variants' prices differ, as the product page shows it. */
    private String price(ProductDetail product) {
        String pesos = pesos(product.fromPrice());
        boolean onePrice = product.variants().stream().allMatch(variant -> variant.price().equals(product.fromPrice()));
        return onePrice ? pesos : message("catalog.preview.from-price", pesos);
    }

    /** The description on one line, cut at a word to at most {@link #DESCRIPTION_LENGTH} characters; empty if none. */
    static Optional<String> description(String description) {
        String text = WHITESPACE.matcher(description).replaceAll(" ").strip();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        if (text.length() <= DESCRIPTION_LENGTH) {
            return Optional.of(text);
        }
        String cut = text.substring(0, DESCRIPTION_LENGTH - 1);
        int lastSpace = cut.lastIndexOf(' ');
        if (lastSpace > 0) {
            cut = cut.substring(0, lastSpace);
        }
        return Optional.of(cut.replaceAll("[\\s.,;:\\-]+$", "") + "…");
    }

    /** The {@link #IMAGE_WIDTH} file of the photo, or its largest one when the photo is smaller. */
    private String imageUrl(ProductView.ImageView image) {
        var file = image.files().stream().filter(candidate -> candidate.width() == IMAGE_WIDTH).findFirst()
            .or(() -> image.files().stream().max(Comparator.comparingInt(ProductView.ImageFile::width)))
            .orElseThrow();
        return storefront + ImageResponse.UPLOADS_PATH + file.fileName();
    }

    /** {@code $ 12.500}, or {@code $ 12.500,50} with cents: as the storefront writes prices. */
    static String pesos(Money amount) {
        var symbols = new DecimalFormatSymbols(CUSTOMER_LOCALE);
        symbols.setGroupingSeparator('.');
        String whole = new DecimalFormat("#,##0", symbols).format(amount.cents() / 100);
        long cents = amount.cents() % 100;
        return "$ " + whole + (cents == 0 ? "" : "," + String.format(Locale.ROOT, "%02d", cents));
    }

    /** {@code product:price:amount}: pesos with two decimals and a point, as Open Graph writes amounts. */
    private static String amount(Money amount) {
        return String.format(Locale.ROOT, "%d.%02d", amount.cents() / 100, amount.cents() % 100);
    }

    private static void meta(StringBuilder page, String attribute, String name, String content) {
        page.append("<meta ").append(attribute).append("=\"").append(name).append("\" content=\"")
            .append(escape(content)).append("\">\n");
    }

    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text, "UTF-8");
    }

    private String message(String key, Object... arguments) {
        return messages.getMessage(key, arguments, CUSTOMER_LOCALE);
    }

    private static ResponseEntity<String> html(HttpStatus status, String body) {
        return ResponseEntity.status(status).contentType(HTML).cacheControl(CacheControl.noCache()).body(body);
    }
}
