package com.pijamaparty.store;

import com.pijamaparty.store.catalog.application.CreateCategory;
import com.pijamaparty.store.catalog.application.CreateProduct;
import com.pijamaparty.store.catalog.application.GenerateVariantMatrix;
import com.pijamaparty.store.catalog.application.ListAllCategories;
import com.pijamaparty.store.catalog.application.ProductDetailsInput;
import com.pijamaparty.store.catalog.application.ProductView;
import com.pijamaparty.store.catalog.application.PublishProduct;
import com.pijamaparty.store.catalog.application.UpdateVariant;
import com.pijamaparty.store.catalog.application.UploadProductImage;
import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.Season;
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
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * A demo catalog for {@code ./mvnw spring-boot:test-run@dev}, whose throwaway database starts empty: four categories,
 * twelve published products with prices, stock (one sold out) and drawn photos, shipping costs and free shipping
 * from ARS 80,000. It goes through the back office's own use cases, so every rule applies, as an administrator that
 * exists only here. It runs only when there are no categories, and {@code -Dstore.dev.demo-catalog=false} turns it
 * off. Without libvips (on the PATH or {@code VIPS_BIN_DIR}) the products keep the gradient of a missing photo. Test
 * sources only: never part of the jar.
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

    private record DemoProduct(String category, String name, String description, String material, Season season,
                               Audience audience, List<String> sizes, List<String> colors, long priceCents,
                               int stock) {}

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
        new DemoProduct("Camisones", "Camisón Puntilla", "Camisón fresco con detalle de puntilla en el escote.",
            "Viscosa", Season.SUMMER, Audience.WOMEN, List.of("S", "M", "L"), List.of("Crudo", "Rosa viejo"),
            3_290_000, 7),
        new DemoProduct("Camisones", "Camisón Satén Luna", "Camisón de satén con breteles regulables.",
            "Satén", Season.MID_SEASON, Audience.WOMEN, List.of("S", "M", "L", "XL"), List.of("Lila", "Negro"),
            3_690_000, 0),
        new DemoProduct("Batas", "Bata Plush Osito", "Bata de plush con capucha y bolsillos.",
            "Plush", Season.WINTER, Audience.WOMEN, List.of("S/M", "L/XL"), List.of("Beige", "Rosa"),
            5_490_000, 5),
        new DemoProduct("Batas", "Bata Toalla", "Bata de toalla con cinturón, para después de la ducha.",
            "Algodón", Season.MID_SEASON, Audience.MEN, List.of("M", "L", "XL"), List.of("Gris", "Azul"),
            4_890_000, 4),
        new DemoProduct("Kigurumis", "Kigurumi Unicornio", "Enterito de polar con capucha y cuerno.",
            "Polar", Season.WINTER, Audience.KIDS, List.of("4", "6", "8", "10"), List.of("Estampado"),
            3_990_000, 9),
        new DemoProduct("Kigurumis", "Kigurumi Dinosaurio", "Enterito de polar con capucha y crestas.",
            "Polar", Season.WINTER, Audience.KIDS, List.of("4", "6", "8"), List.of("Verde"),
            3_990_000, 9),
        new DemoProduct("Kigurumis", "Kigurumi Gatito", "Enterito de polar con orejitas y cola.",
            "Polar", Season.WINTER, Audience.WOMEN, List.of("S", "M", "L"), List.of("Gris", "Rosa"),
            5_190_000, 3));

    @Bean
    ApplicationRunner devCatalogSeedRunner(ListAllCategories listAllCategories, CreateCategory createCategory,
                                           CreateProduct createProduct, GenerateVariantMatrix generateVariantMatrix,
                                           UpdateVariant updateVariant, PublishProduct publishProduct,
                                           UploadProductImage uploadProductImage, AdjustStock adjustStock,
                                           ReplaceShippingZoneRates replaceShippingZoneRates,
                                           SetFreeShippingThreshold setFreeShippingThreshold) {
        return new Runner(listAllCategories, createCategory, createProduct, generateVariantMatrix, updateVariant,
            publishProduct, uploadProductImage, adjustStock, replaceShippingZoneRates, setFreeShippingThreshold);
    }

    private record Runner(ListAllCategories listAllCategories, CreateCategory createCategory,
                          CreateProduct createProduct, GenerateVariantMatrix generateVariantMatrix,
                          UpdateVariant updateVariant, PublishProduct publishProduct,
                          UploadProductImage uploadProductImage, AdjustStock adjustStock,
                          ReplaceShippingZoneRates replaceShippingZoneRates,
                          SetFreeShippingThreshold setFreeShippingThreshold) implements ApplicationRunner {

        @Override
        public void run(ApplicationArguments args) {
            var listed = expect(asAdmin(() -> listAllCategories.execute(new ListAllCategories.Query(SEED_ADMIN))),
                ListAllCategories.Result.Listed.class);
            if (!listed.categories().isEmpty()) {
                log.info("Demo catalog skipped: the database already has categories");
                return;
            }
            Map<String, CategoryId> categories = new LinkedHashMap<>();
            boolean photos = true;
            for (DemoProduct demo : PRODUCTS) {
                CategoryId category = categories.computeIfAbsent(demo.category(), this::category);
                ProductView product = product(category, demo);
                photos = photos && photos(product, demo);
            }
            shipping();
            log.info("Demo catalog created: {} categories, {} products{}", categories.size(), PRODUCTS.size(),
                photos ? "" : " (without photos: install libvips or set VIPS_BIN_DIR)");
        }

        private CategoryId category(String name) {
            return expect(asAdmin(() -> createCategory.execute(new CreateCategory.Command(SEED_ADMIN, name))),
                CreateCategory.Result.Created.class).category().id();
        }

        private ProductView product(CategoryId category, DemoProduct demo) {
            var details = new ProductDetailsInput(category, demo.name(), demo.description(), demo.material(),
                demo.season(), demo.audience());
            ProductId id = expect(asAdmin(() -> createProduct.execute(new CreateProduct.Command(SEED_ADMIN, details))),
                CreateProduct.Result.Created.class).product().id();
            ProductView product = expect(asAdmin(() -> generateVariantMatrix.execute(
                    new GenerateVariantMatrix.Command(SEED_ADMIN, id, demo.sizes(), demo.colors()))),
                GenerateVariantMatrix.Result.Generated.class).product();
            for (ProductView.VariantView variant : product.variants()) {
                price(variant.id(), demo.priceCents());
                if (demo.stock() > 0) {
                    stock(variant.id(), demo.stock());
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

        /** A photo of each color; false, and no more tries, when libvips is missing. */
        private boolean photos(ProductView product, DemoProduct demo) {
            for (String color : demo.colors()) {
                var command = new UploadProductImage.Command(SEED_ADMIN, product.id(),
                    UploadKey.of(UUID.randomUUID()), Optional.of(color), photo(TONES.get(color), demo.name()));
                try {
                    expect(asAdmin(() -> uploadProductImage.execute(command)),
                        UploadProductImage.Result.Uploaded.class);
                } catch (UncheckedIOException e) {
                    log.warn("Demo photos skipped: libvips could not convert them ({})", e.getMessage());
                    return false;
                }
            }
            return true;
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

    /** A soft vertical gradient of the color with two blurred circles, 3:4 as the catalog's photos, as a PNG. */
    private static byte[] photo(Color tone, String seed) {
        var image = new BufferedImage(PHOTO_WIDTH, PHOTO_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setPaint(new GradientPaint(0, 0, mix(tone, Color.WHITE, 0.55), 0, PHOTO_HEIGHT, tone));
        graphics.fillRect(0, 0, PHOTO_WIDTH, PHOTO_HEIGHT);
        int shift = Math.floorMod(seed.hashCode(), 300);
        graphics.setColor(new Color(255, 255, 255, 70));
        graphics.fill(new Ellipse2D.Double(120 + shift, 220, 560, 560));
        graphics.setColor(new Color(255, 255, 255, 40));
        graphics.fill(new Ellipse2D.Double(-120, 700 - shift, 520, 520));
        graphics.dispose();
        try (var bytes = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Color mix(Color from, Color to, double amount) {
        return new Color(
            (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * amount),
            (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
            (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amount));
    }
}
