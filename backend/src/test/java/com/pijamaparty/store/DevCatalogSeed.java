package com.pijamaparty.store;

import com.pijamaparty.store.catalog.application.CreateCategory;
import com.pijamaparty.store.catalog.application.CreateProduct;
import com.pijamaparty.store.catalog.application.EditSizeGuide;
import com.pijamaparty.store.catalog.application.GenerateVariantMatrix;
import com.pijamaparty.store.catalog.application.ListAllCategories;
import com.pijamaparty.store.catalog.application.ProductDetailsInput;
import com.pijamaparty.store.catalog.application.ProductView;
import com.pijamaparty.store.catalog.application.PublishProduct;
import com.pijamaparty.store.catalog.application.UpdateVariant;
import com.pijamaparty.store.catalog.application.UploadProductImage;
import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.Season;
import com.pijamaparty.store.catalog.domain.SizeGuideForm;
import com.pijamaparty.store.catalog.domain.SizeGuideForm.RowForm;
import com.pijamaparty.store.catalog.domain.UploadKey;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.inventory.application.AdjustStock;
import com.pijamaparty.store.inventory.domain.AdjustmentKey;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.shipping.application.ReplaceShippingZoneRates;
import com.pijamaparty.store.shipping.application.SetFreeShippingThreshold;
import com.pijamaparty.store.shipping.domain.Province;
import com.pijamaparty.store.shipping.domain.ShippingZoneRates.TypedRate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * A demo catalog for {@code ./mvnw spring-boot:test-run@dev}, whose throwaway database starts empty: four categories
 * (three with a size guide), sixteen published products with prices, stock (one sold out, others sold out in one
 * color or one size) and drawn photos, shipping costs and free shipping from ARS 80,000. Each color gets up to three
 * drawn views (front, back and a dotted detail to try the zoom) and each product a scene of the whole product, so the
 * gallery has photos to slide between. It goes through the back office's own use cases, so every rule applies, as an
 * administrator that exists only here. It runs only when there are no categories, and
 * {@code -Dstore.dev.demo-catalog=false} turns it off. Without libvips (on the PATH or {@code VIPS_BIN_DIR}) the
 * products keep the gradient of a missing photo. Test sources only: never part of the jar.
 *
 * <p>When the folder of {@code store.dev.demo-photos} ({@code ../treinta-photos} by default, the repository root seen
 * from {@code backend/}) has a {@code catalog.json}, the products are the shop's real ones instead, copied from its
 * Treinta catalog: their names, descriptions, sizes, prices, stock and one photo each from that folder. Treinta has no
 * category, material, season or audience for them, so those were guessed from the names. The folder is not in git.
 */
@TestConfiguration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "store.dev.demo-catalog", havingValue = "true", matchIfMissing = true)
class DevCatalogSeed {

    private static final Logger log = LoggerFactory.getLogger(DevCatalogSeed.class);

    /** The administrator of the seed; the activity log shows its changes without a name. */
    private static final Actor SEED_ADMIN =
        Actor.admin(new UserId(UUID.fromString("00000000-0000-4000-8000-000000000de1")));

    private static final int PHOTO_WIDTH = 900;
    private static final int PHOTO_HEIGHT = 1200;

    /** The tones of colorTones.ts for the colors below, so the drawn photos match the swatches. */
    private static final Map<String, Color> TONES = Map.ofEntries(
        Map.entry("Rosa", new Color(0xf2b8c6)),
        Map.entry("Rosa viejo", new Color(0xd4a5a5)),
        Map.entry("Celeste", new Color(0xa7d3ec)),
        Map.entry("Azul marino", new Color(0x1f2f4d)),
        Map.entry("Azul", new Color(0x2f5d9e)),
        Map.entry("Gris melange", new Color(0xb9b6b1)),
        Map.entry("Gris", new Color(0x9e9e9e)),
        Map.entry("Lila", new Color(0xc8b6e2)),
        Map.entry("Amarillo", new Color(0xf4d35e)),
        Map.entry("Blanco", new Color(0xf7f3ee)),
        Map.entry("Bordó", new Color(0x7b1e2b)),
        Map.entry("Verde", new Color(0x4f7a52)),
        Map.entry("Crudo", new Color(0xefe7d8)),
        Map.entry("Negro", new Color(0x222222)),
        Map.entry("Beige", new Color(0xe7dccb)),
        Map.entry("Estampado", new Color(0xf2b8c6)));

    /** A drawn photo of one color, or the scene of the whole product, with its name in the corner. */
    private enum View {
        FRONT("Frente"), BACK("Espalda"), DETAIL("Detalle"), SCENE("Ambiente");

        /** The views of one color, in gallery order; as many as fit beside the scene in {@link Product#MAX_IMAGES}. */
        static final List<View> OF_A_COLOR = List.of(FRONT, BACK, DETAIL);

        private final String label;

        View(String label) {
            this.label = label;
        }
    }

    /** Colors or sizes listed as sold out get no stock; the other variants get {@code stock} units each. */
    private record DemoProduct(String category, String name, String description, String material, Season season,
                               Audience audience, List<String> sizes, List<String> colors, long priceCents,
                               int stock, List<String> soldOutColors, List<String> soldOutSizes) {

        DemoProduct(String category, String name, String description, String material, Season season,
                    Audience audience, List<String> sizes, List<String> colors, long priceCents, int stock) {
            this(category, name, description, material, season, audience, sizes, colors, priceCents, stock,
                List.of(), List.of());
        }

        int stockOf(ProductView.VariantView variant) {
            return soldOutColors.contains(variant.color()) || soldOutSizes.contains(variant.size()) ? 0 : stock;
        }
    }

    private static final List<DemoProduct> PRODUCTS = List.of(
        new DemoProduct("Pijamas", "Pijama Nubes", "Pijama largo de algodón peinado, suave y abrigado.",
            "Algodón", Season.WINTER, Audience.WOMEN, List.of("S", "M", "L", "XL"), List.of("Rosa", "Celeste"),
            3_890_000, 12),
        new DemoProduct("Pijamas", "Pijama Rayas Clásico", "Camisa con botones y pantalón largo a rayas.",
            "Algodón", Season.MID_SEASON, Audience.MEN, List.of("M", "L", "XL", "XXL"),
            List.of("Azul marino", "Gris melange"), 4_190_000, 8),
        new DemoProduct("Pijamas", "Pijama Estrellitas", "Pijama de dos piezas con estrellas que brillan.",
            "Algodón", Season.WINTER, Audience.KIDS, List.of("4", "6", "8", "10"), List.of("Lila", "Amarillo"),
            2_690_000, 10),
        new DemoProduct("Pijamas", "Pijama Corto Limones", "Remera y short livianos para las noches de verano.",
            "Algodón", Season.SUMMER, Audience.WOMEN, List.of("S", "M", "L"), List.of("Amarillo", "Blanco"),
            2_990_000, 6),
        new DemoProduct("Pijamas", "Pijama Polar Cuadros", "Polar escocés, ideal para los días más fríos.",
            "Polar", Season.WINTER, Audience.MEN, List.of("M", "L", "XL"), List.of("Bordó", "Verde"),
            4_590_000, 2),
        // Four colors, one sold out in every size: its swatch is crossed out (T-E7-36).
        new DemoProduct("Pijamas", "Pijama Corazones", "Pijama de modal con corazones, camisa y pantalón largo.",
            "Modal", Season.MID_SEASON, Audience.WOMEN, List.of("S", "M", "L", "XL"),
            List.of("Rosa", "Lila", "Celeste", "Blanco"), 4_390_000, 6, List.of("Blanco"), List.of()),
        // Six colors: the card shows five swatches and "+1" (D-04).
        new DemoProduct("Pijamas", "Remera Oversize Dormir", "Remera amplia de algodón para dormir o estar en casa.",
            "Algodón", Season.SUMMER, Audience.MEN, List.of("M", "L", "XL"),
            List.of("Negro", "Gris", "Beige", "Rosa", "Verde", "Azul"), 1_990_000, 15),
        new DemoProduct("Camisones", "Camisón Puntilla", "Camisón fresco con detalle de puntilla en el escote.",
            "Viscosa", Season.SUMMER, Audience.WOMEN, List.of("S", "M", "L"), List.of("Crudo", "Rosa viejo"),
            3_290_000, 7),
        new DemoProduct("Camisones", "Camisón Satén Luna", "Camisón de satén con breteles regulables.",
            "Satén", Season.MID_SEASON, Audience.WOMEN, List.of("S", "M", "L", "XL"), List.of("Lila", "Negro"),
            3_690_000, 0),
        // One size sold out in every color: struck through on the card (T-E7-35) and disabled on the page.
        new DemoProduct("Camisones", "Camisón Algodón Flores", "Camisón largo de algodón con estampa de flores.",
            "Algodón", Season.SUMMER, Audience.WOMEN, List.of("S", "M", "L", "XL"), List.of("Celeste", "Rosa"),
            2_890_000, 4, List.of(), List.of("M")),
        new DemoProduct("Batas", "Bata Plush Osito", "Bata de plush con capucha y bolsillos.",
            "Plush", Season.WINTER, Audience.WOMEN, List.of("S/M", "L/XL"), List.of("Beige", "Rosa"),
            5_490_000, 5),
        new DemoProduct("Batas", "Bata Toalla", "Bata de toalla con cinturón, para después de la ducha.",
            "Algodón", Season.MID_SEASON, Audience.MEN, List.of("M", "L", "XL"), List.of("Gris", "Azul"),
            4_890_000, 4),
        new DemoProduct("Batas", "Bata Satén Kimono", "Bata cruzada de satén con mangas anchas y cinturón.",
            "Satén", Season.MID_SEASON, Audience.WOMEN, List.of("S/M", "L/XL"),
            List.of("Negro", "Bordó", "Rosa viejo"), 4_990_000, 3),
        new DemoProduct("Kigurumis", "Kigurumi Unicornio", "Enterito de polar con capucha y cuerno.",
            "Polar", Season.WINTER, Audience.KIDS, List.of("4", "6", "8", "10"), List.of("Estampado"),
            3_990_000, 9),
        new DemoProduct("Kigurumis", "Kigurumi Dinosaurio", "Enterito de polar con capucha y crestas.",
            "Polar", Season.WINTER, Audience.KIDS, List.of("4", "6", "8"), List.of("Verde"),
            3_990_000, 9),
        new DemoProduct("Kigurumis", "Kigurumi Gatito", "Enterito de polar con orejitas y cola.",
            "Polar", Season.WINTER, Audience.WOMEN, List.of("S", "M", "L"), List.of("Gris", "Rosa"),
            5_190_000, 3));

    /** A product of {@code catalog.json}: its photo is a file next to it, for the whole product. */
    private record RealProduct(String photo, String name, String description, String category, String material,
                               Season season, Audience audience, List<RealVariant> variants) {

        List<String> sizes() {
            return variants.stream().map(RealVariant::size).distinct().toList();
        }

        List<String> colors() {
            return variants.stream().map(RealVariant::color).distinct().toList();
        }

        /** Treinta lists only the combinations it sells; the matrix's other ones get the first price and no stock. */
        Offer offerOf(ProductView.VariantView variant) {
            return variants.stream()
                .filter(real -> real.size().equals(variant.size()) && real.color().equals(variant.color()))
                .findFirst()
                .map(real -> new Offer(real.priceCents(), real.stock()))
                .orElseGet(() -> new Offer(variants.getFirst().priceCents(), 0));
        }
    }

    private record RealVariant(String size, String color, long priceCents, int stock) {}

    /** The price and stock a variant gets. */
    private record Offer(long priceCents, int stock) {}

    /** RNF-31.5: the guides of the back office, per category. Kigurumis has none, so its pages show no link. */
    private static final Map<String, SizeGuideForm> SIZE_GUIDES = Map.of(
        "Pijamas", new SizeGuideForm(List.of("Busto (cm)", "Cintura (cm)", "Cadera (cm)", "Largo pantalón (cm)"),
            List.of(
                new RowForm("S", List.of("84–88", "64–68", "90–94", "98")),
                new RowForm("M", List.of("89–93", "69–73", "95–99", "100")),
                new RowForm("L", List.of("94–99", "74–79", "100–105", "102")),
                new RowForm("XL", List.of("100–105", "80–85", "106–111", "104")),
                new RowForm("XXL", List.of("106–112", "86–92", "112–118", "106")),
                new RowForm("4", List.of("56", "54", "60", "")),
                new RowForm("6", List.of("60", "56", "64", "")),
                new RowForm("8", List.of("64", "58", "68", "")),
                new RowForm("10", List.of("68", "61", "73", ""))),
            "Medidas del cuerpo, no de la prenda. Si estás entre dos talles, elegí el más grande."),
        "Camisones", new SizeGuideForm(List.of("Busto (cm)", "Cadera (cm)", "Largo (cm)"),
            List.of(
                new RowForm("S", List.of("84–88", "90–94", "95")),
                new RowForm("M", List.of("89–93", "95–99", "97")),
                new RowForm("L", List.of("94–99", "100–105", "99")),
                new RowForm("XL", List.of("100–105", "106–111", "101"))),
            "El largo se mide desde el hombro."),
        "Batas", new SizeGuideForm(List.of("Busto (cm)", "Largo (cm)", "Manga (cm)"),
            List.of(
                new RowForm("S/M", List.of("84–93", "110", "58")),
                new RowForm("L/XL", List.of("94–105", "114", "60"))),
            null));

    @Bean
    ApplicationRunner devCatalogSeedRunner(ListAllCategories listAllCategories, CreateCategory createCategory,
                                           CreateProduct createProduct, GenerateVariantMatrix generateVariantMatrix,
                                           UpdateVariant updateVariant, PublishProduct publishProduct,
                                           UploadProductImage uploadProductImage, AdjustStock adjustStock,
                                           EditSizeGuide editSizeGuide,
                                           ReplaceShippingZoneRates replaceShippingZoneRates,
                                           SetFreeShippingThreshold setFreeShippingThreshold,
                                           @Value("${store.dev.demo-photos:../treinta-photos}") String realPhotos) {
        return new Runner(listAllCategories, createCategory, createProduct, generateVariantMatrix, updateVariant,
            publishProduct, uploadProductImage, adjustStock, editSizeGuide, replaceShippingZoneRates,
            setFreeShippingThreshold, Path.of(realPhotos));
    }

    private record Runner(ListAllCategories listAllCategories, CreateCategory createCategory,
                          CreateProduct createProduct, GenerateVariantMatrix generateVariantMatrix,
                          UpdateVariant updateVariant, PublishProduct publishProduct,
                          UploadProductImage uploadProductImage, AdjustStock adjustStock,
                          EditSizeGuide editSizeGuide,
                          ReplaceShippingZoneRates replaceShippingZoneRates,
                          SetFreeShippingThreshold setFreeShippingThreshold,
                          Path realPhotos) implements ApplicationRunner {

        @Override
        public void run(ApplicationArguments args) {
            var listed = expect(asAdmin(() -> listAllCategories.execute(new ListAllCategories.Query(SEED_ADMIN))),
                ListAllCategories.Result.Listed.class);
            if (!listed.categories().isEmpty()) {
                log.info("Demo catalog skipped: the database already has categories");
                return;
            }
            if (Files.isRegularFile(realPhotos.resolve("catalog.json"))) {
                realCatalog();
                return;
            }
            Map<String, CategoryId> categories = new LinkedHashMap<>();
            boolean photos = true;
            for (DemoProduct demo : PRODUCTS) {
                CategoryId category = categories.computeIfAbsent(demo.category(), this::category);
                ProductView product = product(category, demo);
                photos = photos && photos(product, demo);
            }
            categories.forEach(this::sizeGuide);
            shipping();
            log.info("Demo catalog created: {} categories, {} products{}", categories.size(), PRODUCTS.size(),
                photos ? "" : " (without photos: install libvips or set VIPS_BIN_DIR)");
        }

        /** The products of {@code catalog.json}, in its order, each with its photo. */
        private void realCatalog() {
            List<RealProduct> products = read(realPhotos.resolve("catalog.json"));
            log.info("Demo catalog: creating the {} real products of {}", products.size(), realPhotos.toAbsolutePath());
            Map<String, CategoryId> categories = new LinkedHashMap<>();
            boolean photos = true;
            for (RealProduct real : products) {
                CategoryId category = categories.computeIfAbsent(real.category(), this::category);
                var details = new ProductDetailsInput(category, real.name(), real.description(), real.material(),
                    real.season(), real.audience());
                ProductView product = product(details, real.sizes(), real.colors(), real::offerOf);
                photos = photos && upload(product, Optional.empty(), bytes(realPhotos.resolve(real.photo())));
            }
            categories.forEach(this::sizeGuide);
            shipping();
            log.info("Demo catalog created: {} categories, {} real products{}", categories.size(), products.size(),
                photos ? "" : " (without photos: install libvips or set VIPS_BIN_DIR)");
        }

        private static List<RealProduct> read(Path catalog) {
            try {
                return new ObjectMapper().readValue(catalog.toFile(), new TypeReference<>() {});
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static byte[] bytes(Path photo) {
            try {
                return Files.readAllBytes(photo);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private CategoryId category(String name) {
            return expect(asAdmin(() -> createCategory.execute(new CreateCategory.Command(SEED_ADMIN, name))),
                CreateCategory.Result.Created.class).category().id();
        }

        private void sizeGuide(String name, CategoryId category) {
            SizeGuideForm form = SIZE_GUIDES.get(name);
            if (form != null) {
                expect(asAdmin(() -> editSizeGuide.execute(new EditSizeGuide.Command(SEED_ADMIN, category, form))),
                    EditSizeGuide.Result.Saved.class);
            }
        }

        private ProductView product(CategoryId category, DemoProduct demo) {
            var details = new ProductDetailsInput(category, demo.name(), demo.description(), demo.material(),
                demo.season(), demo.audience());
            return product(details, demo.sizes(), demo.colors(),
                variant -> new Offer(demo.priceCents(), demo.stockOf(variant)));
        }

        private ProductView product(ProductDetailsInput details, List<String> sizes, List<String> colors,
                                    Function<ProductView.VariantView, Offer> offers) {
            ProductId id = expect(asAdmin(() -> createProduct.execute(new CreateProduct.Command(SEED_ADMIN, details))),
                CreateProduct.Result.Created.class).product().id();
            ProductView product = expect(asAdmin(() -> generateVariantMatrix.execute(
                    new GenerateVariantMatrix.Command(SEED_ADMIN, id, sizes, colors))),
                GenerateVariantMatrix.Result.Generated.class).product();
            for (ProductView.VariantView variant : product.variants()) {
                Offer offer = offers.apply(variant);
                price(variant.id(), offer.priceCents());
                if (offer.stock() > 0) {
                    stock(variant.id(), offer.stock());
                }
            }
            expect(asAdmin(() -> publishProduct.execute(new PublishProduct.Command(SEED_ADMIN, id))),
                PublishProduct.Result.Published.class);
            return product;
        }

        private void price(VariantId variant, long priceCents) {
            expect(asAdmin(() -> updateVariant.execute(
                    new UpdateVariant.Command(SEED_ADMIN, variant, Optional.of(priceCents), Optional.empty()))),
                UpdateVariant.Result.Updated.class);
        }

        private void stock(VariantId variant, int quantity) {
            var command = new AdjustStock.Command(SEED_ADMIN,
                new com.pijamaparty.store.inventory.domain.VariantId(variant.value()),
                AdjustmentKey.of(UUID.randomUUID()), quantity, Optional.empty());
            expect(asAdmin(() -> adjustStock.execute(command)), AdjustStock.Result.Adjusted.class);
        }

        /**
         * The views of each color, as many as fit beside the scene of the whole product, then that scene; false, and
         * no more tries, when libvips is missing.
         */
        private boolean photos(ProductView product, DemoProduct demo) {
            int views = Math.min(View.OF_A_COLOR.size(), (Product.MAX_IMAGES - 1) / demo.colors().size());
            for (String color : demo.colors()) {
                for (View view : View.OF_A_COLOR.subList(0, views)) {
                    if (!upload(product, Optional.of(color), photo(TONES.get(color), demo.name(), view))) {
                        return false;
                    }
                }
            }
            Color first = TONES.get(demo.colors().getFirst());
            return upload(product, Optional.empty(), photo(first, demo.name(), View.SCENE));
        }

        private boolean upload(ProductView product, Optional<String> color, byte[] photo) {
            var command = new UploadProductImage.Command(SEED_ADMIN, product.id(), UploadKey.of(UUID.randomUUID()),
                color, photo);
            try {
                expect(asAdmin(() -> uploadProductImage.execute(command)), UploadProductImage.Result.Uploaded.class);
                return true;
            } catch (UncheckedIOException e) {
                log.warn("Demo photos skipped: libvips could not convert them ({})", e.getMessage());
                return false;
            }
        }

        /** Santa Fe and its neighbors, as the business ships today, and free shipping from ARS 80,000. */
        private void shipping() {
            var rates = List.of(
                new TypedRate(Province.SANTA_FE, 500_000),
                new TypedRate(Province.ENTRE_RIOS, 800_000),
                new TypedRate(Province.CORDOBA, 900_000),
                new TypedRate(Province.BUENOS_AIRES, 1_000_000),
                new TypedRate(Province.CIUDAD_DE_BUENOS_AIRES, 1_000_000));
            expect(asAdmin(() -> replaceShippingZoneRates.execute(
                    new ReplaceShippingZoneRates.Command(SEED_ADMIN, rates))),
                ReplaceShippingZoneRates.Result.Replaced.class);
            expect(asAdmin(() -> setFreeShippingThreshold.execute(
                    new SetFreeShippingThreshold.Command(SEED_ADMIN, Optional.of(8_000_000L)))),
                SetFreeShippingThreshold.Result.Saved.class);
        }
    }

    private static <T> T asAdmin(Supplier<T> block) {
        return CurrentActor.callAs(SEED_ADMIN, block);
    }

    /** The seed knows its data is valid: any other answer is a bug in the seed or a changed rule. */
    private static <T> T expect(Object result, Class<T> expected) {
        if (!expected.isInstance(result)) {
            throw new IllegalStateException("Demo catalog: expected " + expected.getSimpleName() + ", got " + result);
        }
        return expected.cast(result);
    }

    /**
     * One drawn view, 3:4 as the catalog's photos, as a PNG: a garment on a soft gradient of the color (front, and
     * back with the gradient reversed), small dots to try the zoom (detail), or the garment under a night sky
     * (scene). Its name sits in the corner, so the gallery shows which photo it slid to.
     */
    private static byte[] photo(Color tone, String seed, View view) {
        var image = new BufferedImage(PHOTO_WIDTH, PHOTO_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color light = mix(tone, Color.WHITE, 0.55);
        int shift = Math.floorMod(seed.hashCode(), 300);
        switch (view) {
            case FRONT, BACK -> {
                boolean back = view == View.BACK;
                graphics.setPaint(new GradientPaint(0, 0, back ? tone : light, 0, PHOTO_HEIGHT, back ? light : tone));
                graphics.fillRect(0, 0, PHOTO_WIDTH, PHOTO_HEIGHT);
                graphics.setColor(new Color(255, 255, 255, 60));
                graphics.fill(new Ellipse2D.Double(120 + shift, 180, 560, 560));
                garment(graphics, tone, back);
            }
            case DETAIL -> {
                graphics.setColor(light);
                graphics.fillRect(0, 0, PHOTO_WIDTH, PHOTO_HEIGHT);
                graphics.setColor(mix(tone, Color.BLACK, 0.15));
                for (int row = 0; row * 36 < PHOTO_HEIGHT; row++) {
                    for (int x = row % 2 * 18; x < PHOTO_WIDTH; x += 36) {
                        graphics.fill(new Ellipse2D.Double(x, row * 36, 9, 9));
                    }
                }
                graphics.setColor(new Color(255, 255, 255, 150));
                graphics.setStroke(new BasicStroke(10, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.draw(new QuadCurve2D.Double(-40, 760, 450, 560 + shift, 940, 820));
            }
            case SCENE -> {
                Color night = new Color(0x3a3260);
                graphics.setPaint(new GradientPaint(0, 0, night, PHOTO_WIDTH, PHOTO_HEIGHT, mix(tone, night, 0.4)));
                graphics.fillRect(0, 0, PHOTO_WIDTH, PHOTO_HEIGHT);
                graphics.setColor(new Color(0xfff4d6));
                graphics.fill(new Ellipse2D.Double(560 - shift / 3.0, 110, 190, 190));
                graphics.setColor(night);
                graphics.fill(new Ellipse2D.Double(610 - shift / 3.0, 85, 170, 170));
                graphics.setColor(new Color(255, 255, 255, 180));
                for (int star = 0; star < 24; star++) {
                    int x = Math.floorMod(seed.hashCode() * (star + 7), PHOTO_WIDTH);
                    int y = Math.floorMod(seed.hashCode() * (star + 3) / 7, 520);
                    graphics.fill(new Ellipse2D.Double(x, y, 7, 7));
                }
                garment(graphics, tone, false);
            }
        }
        label(graphics, view.label, view == View.SCENE ? Color.WHITE : mix(tone, Color.BLACK, 0.55));
        graphics.dispose();
        try (var bytes = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A plain shirt over trousers in the color; from the back it has no buttons and no neckline. */
    private static void garment(Graphics2D graphics, Color tone, boolean back) {
        var shirt = new Path2D.Double();
        shirt.moveTo(330, 260);
        shirt.lineTo(570, 260);
        shirt.lineTo(730, 360);
        shirt.lineTo(680, 470);
        shirt.lineTo(610, 430);
        shirt.lineTo(610, 690);
        shirt.lineTo(290, 690);
        shirt.lineTo(290, 430);
        shirt.lineTo(220, 470);
        shirt.lineTo(170, 360);
        shirt.closePath();
        graphics.setColor(mix(tone, Color.BLACK, 0.12));
        graphics.fill(new RoundRectangle2D.Double(300, 670, 135, 400, 40, 40));
        graphics.fill(new RoundRectangle2D.Double(465, 670, 135, 400, 40, 40));
        graphics.setColor(tone);
        graphics.fill(shirt);
        graphics.setColor(mix(tone, Color.BLACK, 0.25));
        graphics.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(shirt);
        if (back) {
            graphics.drawLine(330, 300, 570, 300);
        } else {
            graphics.draw(new Arc2D.Double(390, 220, 120, 90, 180, 180, Arc2D.OPEN));
            for (int y = 360; y <= 620; y += 65) {
                graphics.fill(new Ellipse2D.Double(442, y, 16, 16));
            }
        }
    }

    private static void label(Graphics2D graphics, String text, Color color) {
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 46));
        graphics.setColor(color);
        graphics.drawString(text, 56, PHOTO_HEIGHT - 64);
    }

    private static Color mix(Color from, Color to, double amount) {
        return new Color(
            (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * amount),
            (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
            (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amount));
    }
}
