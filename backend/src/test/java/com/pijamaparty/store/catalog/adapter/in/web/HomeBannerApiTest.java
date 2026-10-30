package com.pijamaparty.store.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.catalog.application.TestImages;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.util.FileSystemUtils;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * UC-32 through the back office API and the home page's endpoint, with the real security filter chain, PostgreSQL and
 * files. The conversion is the fake processor of {@link ProductImageAdminApiTest}, whose context this test shares.
 */
@ApiTest
@Import(ProductImageAdminApiTest.FakeImageProcessor.class)
@TestPropertySource(properties = "store.images.uploads-dir=${java.io.tmpdir}/pijamaparty-test-uploads")
class HomeBannerApiTest {

    private static final String BANNER = "/api/admin/home/banner";
    private static final String HOME_BANNER = "/api/home/banner";
    private static final RequestPostProcessor ADMIN = user(UUID.randomUUID().toString()).roles("ADMIN");
    private static final byte[] PHOTO = TestImages.png(1600, 1200);
    private static final byte[] OTHER_PHOTO = TestImages.png(1200, 1600);
    private static final String TEXTS = """
        {"title": " La noche se llena de personajes ", "body": "Una pijamada para jugar.",
         "linkText": "Descubrí la colección", "linkPath": "/categoria/kigurumis"}
        """;

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate owner;
    private final Path uploads;

    HomeBannerApiTest(MockMvc mvc, ObjectMapper json, PostgreSQLContainer<?> postgres,
                      @Value("${store.images.uploads-dir}") Path uploads) {
        this.mvc = mvc;
        this.json = json;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
        this.uploads = uploads;
    }

    @AfterEach
    void cleanUp() throws IOException {
        owner.update("""
            update home_banner
            set title = null, body = null, link_text = null, link_path = null, image_storage_key = null,
                image_placeholder = null, image_upload_key = null, image_content_sha256 = null, published = false
            """);
        FileSystemUtils.deleteRecursively(uploads);
    }

    @Test
    void givenANewShop_whenTheBannerIsRead_thenTheHomePageGetsNoneAndTheBackOfficeAnEmptyHiddenOne() throws Exception {
        mvc.perform(get(HOME_BANNER)).andExpect(status().isNoContent());
        mvc.perform(get(BANNER).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value(nullValue()))
            .andExpect(jsonPath("$.image").value(nullValue()))
            .andExpect(jsonPath("$.published").value(false));
    }

    @Test
    void givenABannerWithAPhotoTitleLinkTextAndTarget_whenPublished_thenTheHomePageShowsIt() throws Exception {
        send(put(BANNER), TEXTS).andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("La noche se llena de personajes"));
        upload(UUID.randomUUID().toString(), PHOTO).andExpect(status().isCreated());

        send(post(BANNER + "/publish"), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.published").value(true));

        mvc.perform(get(HOME_BANNER))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.title").value("La noche se llena de personajes"))
            .andExpect(jsonPath("$.body").value("Una pijamada para jugar."))
            .andExpect(jsonPath("$.linkText").value("Descubrí la colección"))
            .andExpect(jsonPath("$.linkPath").value("/categoria/kigurumis"))
            .andExpect(jsonPath("$.image.placeholder", startsWith("data:image/webp;base64,")))
            .andExpect(jsonPath("$.image.files[*].width", contains(400, 600, 800, 1200)));
    }

    @Test
    void givenAPublishedBanner_whenHidden_thenTheHomePageGetsNone() throws Exception {
        send(put(BANNER), TEXTS);
        upload(UUID.randomUUID().toString(), PHOTO);
        send(post(BANNER + "/publish"), null).andExpect(status().isOk());

        send(post(BANNER + "/hide"), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.published").value(false));
        mvc.perform(get(HOME_BANNER)).andExpect(status().isNoContent());
    }

    @Test
    void givenABannerWithoutAPhoto_whenPublished_thenItIsRefusedWith409NamingThePhoto() throws Exception {
        send(post(BANNER + "/publish"), null)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.missing", contains("photo", "title", "linkText", "linkPath")));
        send(put(BANNER), TEXTS);

        send(post(BANNER + "/publish"), null)
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.problem").value("INCOMPLETE_BANNER"))
            .andExpect(jsonPath("$.missing", contains("photo")));
        mvc.perform(get(HOME_BANNER)).andExpect(status().isNoContent());
    }

    @Test
    void givenATargetOutsideTheStore_whenSaved_thenItIsRefusedWith422() throws Exception {
        for (String target : List.of("https://other.site", "//other.site", "/\\other.site")) {
            send(put(BANNER), json.writeValueAsString(new HomeBannerAdminController.HomeBannerRequest(
                "Personajes", "", "Ver", target)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.problem").value("INVALID_BANNER"))
                .andExpect(jsonPath("$.fields", contains("linkPath")));
        }
        send(put(BANNER), json.writeValueAsString(new HomeBannerAdminController.HomeBannerRequest(
            "a".repeat(61), null, " ", "/")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.fields", contains("title", "linkText")));

        mvc.perform(get(BANNER).with(ADMIN)).andExpect(jsonPath("$.title").value(nullValue()));
    }

    @Test
    void givenAReplacedPhoto_whenSaved_thenTheOldFilesAreDeletedAndTheHomePageShowsTheNewOne() throws Exception {
        send(put(BANNER), TEXTS);
        JsonNode first = body(upload(UUID.randomUUID().toString(), PHOTO).andExpect(status().isCreated()));
        send(post(BANNER + "/publish"), null);

        JsonNode second = body(upload(UUID.randomUUID().toString(), OTHER_PHOTO)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.published").value(true)));

        for (String url : urls(first)) {
            assertThat(uploads.resolve(url.substring("/uploads/".length()))).doesNotExist();
        }
        for (String url : urls(second)) {
            assertThat(uploads.resolve(url.substring("/uploads/".length()))).exists();
        }
        assertThat(webpFiles()).hasSize(4);
        mvc.perform(get(HOME_BANNER))
            .andExpect(jsonPath("$.image.files[0].url").value(urls(second).getFirst()));
    }

    @Test
    void givenAnUploadRetried_whenTheKeyComesWithTheSameOrAnotherFile_thenItIsARetryOrRefused() throws Exception {
        String key = UUID.randomUUID().toString();
        JsonNode first = body(upload(key, PHOTO).andExpect(status().isCreated()));

        upload(key, PHOTO)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.image.files[0].url").value(urls(first).getFirst()));
        upload(key, OTHER_PHOTO)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.problem").value("KEY_REUSED"));
        upload(UUID.randomUUID().toString(), "not a photo".getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.problem").value("UNSUPPORTED_FORMAT"));
        upload("not-a-uuid", PHOTO)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid Idempotency-Key"));

        assertThat(webpFiles()).hasSize(4);
    }

    @Test
    void givenAVisitorOrACustomerSession_whenCallingTheAdminEndpoints_thenTheyGet401Or403AndNothingChanges()
        throws Exception {
        RequestPostProcessor customer = user(UUID.randomUUID().toString()).roles("CUSTOMER");

        mvc.perform(get(BANNER)).andExpect(status().isUnauthorized());
        mvc.perform(put(BANNER).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(TEXTS))
            .andExpect(status().isUnauthorized());
        mvc.perform(post(BANNER + "/publish").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(get(BANNER).with(customer)).andExpect(status().isForbidden());
        mvc.perform(put(BANNER).with(customer).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(TEXTS))
            .andExpect(status().isForbidden());
        mvc.perform(post(BANNER + "/publish").with(customer).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(BANNER + "/hide").with(customer).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(multipart(BANNER + "/photo").file(file(PHOTO)).header("Idempotency-Key", UUID.randomUUID())
                .with(customer).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(put(BANNER).with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(TEXTS))
            .andExpect(status().isForbidden());

        mvc.perform(get(BANNER).with(ADMIN))
            .andExpect(jsonPath("$.title").value(nullValue()))
            .andExpect(jsonPath("$.image").value(nullValue()));
        assertThat(webpFiles()).isEmpty();
    }

    private ResultActions upload(String key, byte[] content) throws Exception {
        return mvc.perform(multipart(BANNER + "/photo").file(file(content)).header("Idempotency-Key", key)
            .with(ADMIN).with(csrf()));
    }

    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "personajes.jpg", MediaType.IMAGE_JPEG_VALUE, content);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.with(ADMIN).with(csrf());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static List<String> urls(JsonNode banner) {
        var urls = new ArrayList<String>();
        banner.get("image").get("files").forEach(file -> urls.add(file.get("url").asText()));
        return urls;
    }

    private List<Path> webpFiles() throws IOException {
        if (!Files.exists(uploads)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(uploads)) {
            return files.filter(path -> path.toString().endsWith(".webp")).toList();
        }
    }
}
