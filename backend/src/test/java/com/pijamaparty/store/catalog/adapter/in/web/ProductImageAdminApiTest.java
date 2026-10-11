package com.pijamaparty.store.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.catalog.application.ImageProcessor;
import com.pijamaparty.store.catalog.application.InMemoryImageProcessor;
import com.pijamaparty.store.catalog.application.TestImages;
import com.pijamaparty.store.catalog.application.UploadProductImage;
import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.Season;
import com.pijamaparty.store.catalog.domain.UploadKey;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.util.FileSystemUtils;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-06 photos through the back office API, with the real security filter chain, PostgreSQL and files. The conversion
 * is the fake processor, so this runs without libvips; the libvips adapter has its own contract test.
 */
@ApiTest
@Import(ProductImageAdminApiTest.FakeImageProcessor.class)
@TestPropertySource(properties = "store.images.uploads-dir=${java.io.tmpdir}/pijamaparty-test-uploads")
class ProductImageAdminApiTest {

    private static final String PRODUCTS = "/api/admin/catalog/products";
    private static final RequestPostProcessor ADMIN = user(UUID.randomUUID().toString()).roles("ADMIN");
    private static final byte[] PHOTO = TestImages.png(1600, 1200);
    private static final byte[] OTHER_PHOTO = TestImages.png(1200, 1600);

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final UploadProductImage uploadProductImage;
    private final JdbcTemplate owner;
    private final Path uploads;
    private final SaturableImageProcessor processor;
    private String product;

    ProductImageAdminApiTest(MockMvc mvc, ObjectMapper json, UploadProductImage uploadProductImage,
                             PostgreSQLContainer<?> postgres, @Value("${store.images.uploads-dir}") Path uploads,
                             SaturableImageProcessor processor) {
        this.mvc = mvc;
        this.json = json;
        this.uploadProductImage = uploadProductImage;
        this.processor = processor;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
        this.uploads = uploads;
    }

    @BeforeEach
    void createProduct() throws Exception {
        String category = body(send(post("/api/admin/catalog/categories"), "{\"name\":\"Verano\"}")).get("id").asText();
        product = body(send(post(PRODUCTS), json.writeValueAsString(new ProductAdminController.ProductRequest(
            UUID.fromString(category), "Pijama", "", "Algodón", Season.SUMMER, Audience.WOMEN)))).get("id").asText();
        send(post(PRODUCTS + "/" + product + "/variants/generate"), "{\"sizes\":[\"M\"],\"colors\":[\"Rosa\"]}")
            .andExpect(status().isOk());
    }

    @AfterEach
    void cleanUp() throws IOException {
        processor.saturated = false;
        owner.execute("truncate product_image, variant, product, category, stock_movement, stock_level");
        FileSystemUtils.deleteRecursively(uploads);
    }

    @Test
    void givenAPhoto_whenUploaded_thenItsWebpFilesAreStoredAndTheProductShowsIt() throws Exception {
        JsonNode image = body(upload(UUID.randomUUID().toString(), PHOTO, null)
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.color").value(nullValue()))
            .andExpect(jsonPath("$.placeholder", startsWith("data:image/webp;base64,")))
            .andExpect(jsonPath("$.files[*].width", contains(400, 800, 1200)))
            .andExpect(jsonPath("$.files[0].url", matchesPattern("/uploads/[0-9a-f-]{36}-400\\.webp"))));

        for (JsonNode file : image.get("files")) {
            String name = file.get("url").asText().substring("/uploads/".length());
            assertThat(TestImages.webpDimensions(Files.readAllBytes(uploads.resolve(name)))).isPresent();
        }
        mvc.perform(get(PRODUCTS + "/" + product).with(ADMIN))
            .andExpect(jsonPath("$.images.length()").value(1))
            .andExpect(jsonPath("$.images[0].id").value(image.get("id").asText()));
        upload(UUID.randomUUID().toString(), OTHER_PHOTO, "Rosa")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.color").value("Rosa"));
    }

    @Test
    void givenAnUploadCutByALostConnection_whenRetried_thenThePhotoIsNotDuplicated() throws Exception {
        String key = UUID.randomUUID().toString();
        String id = body(upload(key, PHOTO, null).andExpect(status().isCreated())).get("id").asText();

        upload(key, PHOTO, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id));
        upload(key, OTHER_PHOTO, null)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("KEY_REUSED"));

        assertThat(owner.queryForObject("select count(*) from product_image", Integer.class)).isOne();
        assertThat(webpFiles()).hasSize(3);
    }

    @Test
    void givenSameKeyUploadsAtOnce_whenTheyRun_thenOnePhotoIsStoredAndTheOthersAnswerIt() throws Exception {
        Actor admin = Actor.admin(UserId.of(UUID.randomUUID()));
        var command = new UploadProductImage.Command(admin, ProductId.of(UUID.fromString(product)),
            UploadKey.of(UUID.randomUUID()), Optional.empty(), PHOTO);
        var start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<UploadProductImage.Result> outcomes = new ArrayList<>();
        try {
            List<Future<UploadProductImage.Result>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return CurrentActor.callAs(admin, () -> uploadProductImage.execute(command));
                }));
            }
            start.countDown();
            for (Future<UploadProductImage.Result> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(outcomes).filteredOn(UploadProductImage.Result.Uploaded.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(UploadProductImage.Result.AlreadyUploaded.class::isInstance).hasSize(3);
        assertThat(owner.queryForObject("select count(*) from product_image", Integer.class)).isOne();
        assertThat(webpFiles()).hasSize(3);
    }

    @Test
    void givenNoConversionSlotFreesUp_whenAPhotoIsUploaded_thenItAnswers503WithRetryAfterAndTheRetryStoresIt()
        throws Exception {
        String key = UUID.randomUUID().toString();
        processor.saturated = true;

        upload(key, PHOTO, null)
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(owner.queryForObject("select count(*) from product_image", Integer.class)).isZero();
        assertThat(webpFiles()).isEmpty();

        processor.saturated = false;
        upload(key, PHOTO, null).andExpect(status().isCreated());
        assertThat(owner.queryForObject("select count(*) from product_image", Integer.class)).isOne();
    }

    @Test
    void givenFilesThatAreNotAcceptedPhotos_whenUploaded_thenTheyAreRejectedAsProblemJson() throws Exception {
        upload(UUID.randomUUID().toString(), "not a photo".getBytes(StandardCharsets.UTF_8), null)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.title").value("Invalid photo"))
            .andExpect(jsonPath("$.problem").value("UNSUPPORTED_FORMAT"));
        upload(UUID.randomUUID().toString(), TestImages.pngHeaderOnly(20_000, 20_000), null)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.problem").value("TOO_LARGE"));
        upload(UUID.randomUUID().toString(), PHOTO, "Verde")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.problem").value("UNKNOWN_COLOR"));
        upload("not-a-uuid", PHOTO, null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid Idempotency-Key"));
        mvc.perform(multipart(PRODUCTS + "/" + product + "/images").file(file(PHOTO)).with(ADMIN).with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(multipart(PRODUCTS + "/" + product + "/images").header("Idempotency-Key", UUID.randomUUID())
                .with(ADMIN).with(csrf()))
            .andExpect(status().isBadRequest());
        upload(UUID.randomUUID().toString(), PHOTO, "x".repeat(121)).andExpect(status().isBadRequest());

        assertThat(owner.queryForObject("select count(*) from product_image", Integer.class)).isZero();
        assertThat(webpFiles()).isEmpty();
    }

    @Test
    void givenAProductWith8Photos_whenAnotherIsUploaded_thenItIsRejected() throws Exception {
        for (int i = 0; i < 8; i++) {
            upload(UUID.randomUUID().toString(), PHOTO, null).andExpect(status().isCreated());
        }

        upload(UUID.randomUUID().toString(), PHOTO, null)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Too many photos"));
        upload(UUID.randomUUID().toString(), PHOTO, null, UUID.randomUUID().toString())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Product not found"));
        assertThat(webpFiles()).hasSize(24);
    }

    @Test
    void givenPhotos_whenReorderedAndOneDeleted_thenTheProductShowsTheRestInOrderAndItsFilesAreGone()
        throws Exception {
        String a = body(upload(UUID.randomUUID().toString(), PHOTO, null)).get("id").asText();
        JsonNode b = body(upload(UUID.randomUUID().toString(), PHOTO, null));
        String c = body(upload(UUID.randomUUID().toString(), PHOTO, null)).get("id").asText();
        String bId = b.get("id").asText();

        send(put(PRODUCTS + "/" + product + "/images/order"), order(c, bId, a))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.images[*].id", contains(c, bId, a)));
        send(put(PRODUCTS + "/" + product + "/images/order"), order(c, a))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.title").value("Invalid order"));

        mvc.perform(delete(PRODUCTS + "/" + product + "/images/" + bId).with(ADMIN).with(csrf()))
            .andExpect(status().isNoContent());

        mvc.perform(get(PRODUCTS + "/" + product).with(ADMIN))
            .andExpect(jsonPath("$.images[*].id", contains(c, a)));
        for (JsonNode file : b.get("files")) {
            assertThat(uploads.resolve(file.get("url").asText().substring("/uploads/".length()))).doesNotExist();
        }
        assertThat(webpFiles()).hasSize(6);
        mvc.perform(delete(PRODUCTS + "/" + product + "/images/" + bId).with(ADMIN).with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Photo not found"));
    }

    @Test
    void givenSomeoneOtherThanTheAdministratorOrNoCsrfToken_whenChangingPhotos_thenItIsRefused() throws Exception {
        String image = body(upload(UUID.randomUUID().toString(), PHOTO, null)).get("id").asText();

        mvc.perform(multipart(PRODUCTS + "/" + product + "/images").file(file(PHOTO))
                .header("Idempotency-Key", UUID.randomUUID()).with(ADMIN))
            .andExpect(status().isForbidden());
        mvc.perform(multipart(PRODUCTS + "/" + product + "/images").file(file(PHOTO))
                .header("Idempotency-Key", UUID.randomUUID()).with(csrf())
                .with(user(UUID.randomUUID().toString()).roles("CUSTOMER")))
            .andExpect(status().isForbidden());
        mvc.perform(put(PRODUCTS + "/" + product + "/images/order").with(ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content(order(image)))
            .andExpect(status().isForbidden());
        mvc.perform(delete(PRODUCTS + "/" + product + "/images/" + image).with(ADMIN))
            .andExpect(status().isForbidden());

        mvc.perform(get(PRODUCTS + "/" + product).with(ADMIN)).andExpect(jsonPath("$.images.length()").value(1));
    }

    private ResultActions upload(String key, byte[] content, String color) throws Exception {
        return upload(key, content, color, product);
    }

    private ResultActions upload(String key, byte[] content, String color, String productId) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart(PRODUCTS + "/" + productId + "/images");
        request.file(file(content)).header("Idempotency-Key", key);
        if (color != null) {
            request.param("color", color);
        }
        return mvc.perform(request.with(ADMIN).with(csrf()));
    }

    /** Whatever the phone says, the name and type of the part never matter (RNF-29.4). */
    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "IMG_0001.jpg", MediaType.IMAGE_JPEG_VALUE, content);
    }

    private String order(String... ids) throws Exception {
        return json.writeValueAsString(new ProductImageAdminController.ImageOrderRequest(
            Stream.of(ids).map(UUID::fromString).toList()));
    }

    private List<Path> webpFiles() throws IOException {
        if (!Files.exists(uploads)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(uploads)) {
            return files.filter(path -> path.toString().endsWith(".webp")).toList();
        }
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String body) throws Exception {
        return mvc.perform(request.with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeImageProcessor {

        @Bean
        @Primary
        SaturableImageProcessor fakeImageProcessor() {
            return new SaturableImageProcessor();
        }
    }

    /** The fake; while saturated it refuses like the conversion limit does when no slot frees up (T-E1-14). */
    static class SaturableImageProcessor extends InMemoryImageProcessor {

        volatile boolean saturated;

        @Override
        public ImageProcessor.Result process(byte[] content) {
            if (saturated) {
                throw new RejectedExecutionException("no image conversion slot");
            }
            return super.process(content);
        }
    }
}
