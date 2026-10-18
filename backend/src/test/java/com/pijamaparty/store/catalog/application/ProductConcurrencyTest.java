package com.pijamaparty.store.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Season;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** UC-06 on PostgreSQL with committed transactions: requests that race keep the rules of the use case. */
@ApiTest
class ProductConcurrencyTest {

    private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));
    private static final int REQUESTS = 4;

    private final CreateCategory createCategory;
    private final CreateProduct createProduct;
    private final GenerateVariantMatrix generateVariantMatrix;
    private final UpdateVariant updateVariant;
    private final PublishProduct publishProduct;
    private final JdbcTemplate owner;
    private CategoryId category;

    ProductConcurrencyTest(CreateCategory createCategory, CreateProduct createProduct,
                           GenerateVariantMatrix generateVariantMatrix, UpdateVariant updateVariant,
                           PublishProduct publishProduct, PostgreSQLContainer<?> postgres) {
        this.createCategory = createCategory;
        this.createProduct = createProduct;
        this.generateVariantMatrix = generateVariantMatrix;
        this.updateVariant = updateVariant;
        this.publishProduct = publishProduct;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @BeforeEach
    void createCategory() {
        var created = (CreateCategory.Result.Created) CurrentActor.callAs(ADMIN, () ->
            createCategory.execute(new CreateCategory.Command(ADMIN, "Verano")));
        category = created.category().id();
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate product_image, variant, product, size_guide, category, stock_movement, stock_level");
    }

    @Test
    void givenTheSameMatrixGeneratedSeveralTimesAtOnce_whenTheyRun_thenEachCombinationExistsOnceWithItsStock()
        throws Exception {
        ProductView product = create("Pijama");
        List<Callable<Object>> requests = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            requests.add(() -> generateVariantMatrix.execute(new GenerateVariantMatrix.Command(ADMIN, product.id(),
                List.of("S", "M", "L"), List.of("Rosa", "Celeste"))));
        }

        List<Object> outcomes = runAtOnce(requests);

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome).isInstanceOf(
            GenerateVariantMatrix.Result.Generated.class));
        assertThat(outcomes).extracting(outcome -> ((GenerateVariantMatrix.Result.Generated) outcome).created())
            .containsExactlyInAnyOrder(6, 0, 0, 0);
        assertThat(owner.queryForObject("select count(*) from variant where product_id = ?", Integer.class,
            product.id().value())).isEqualTo(6);
        assertThat(owner.queryForObject("""
            select count(*) from stock_level s join variant v on v.id = s.variant_id where v.product_id = ?
            """, Integer.class, product.id().value())).isEqualTo(6);
    }

    @Test
    void givenSeveralRequestsCreatingTheSameSlugAtOnce_whenTheyRun_thenOneIsCreatedAndTheOthersAreRejected()
        throws Exception {
        List<Callable<Object>> requests = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            String name = i % 2 == 0 ? "Pijama de Algodón" : "PIJAMA DE ALGODON";
            requests.add(() -> createProduct.execute(new CreateProduct.Command(ADMIN, details(name))));
        }

        List<Object> outcomes = runAtOnce(requests);

        assertThat(outcomes).filteredOn(CreateProduct.Result.Created.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(CreateProduct.Result.SlugAlreadyUsed.class::isInstance)
            .hasSize(REQUESTS - 1);
        assertThat(owner.queryForObject("select count(*) from product", Integer.class)).isOne();
    }

    @Test
    void givenAPublishedProductWithTwoPricedVariants_whenBothAreDeactivatedAtOnce_thenOneStaysActive()
        throws Exception {
        for (int round = 0; round < 3; round++) {
            ProductView product = create("Pijama " + round);
            var generated = (GenerateVariantMatrix.Result.Generated) CurrentActor.callAs(ADMIN, () ->
                generateVariantMatrix.execute(new GenerateVariantMatrix.Command(ADMIN, product.id(),
                    List.of("S", "M"), List.of("Rosa"))));
            List<ProductView.VariantView> variants = generated.product().variants();
            variants.forEach(variant -> CurrentActor.callAs(ADMIN, () -> updateVariant.execute(
                new UpdateVariant.Command(ADMIN, variant.id(), Optional.of(1_500_000L), Optional.empty()))));
            CurrentActor.callAs(ADMIN, () -> publishProduct.execute(new PublishProduct.Command(ADMIN, product.id())));

            List<Object> outcomes = runAtOnce(variants.stream().<Callable<Object>>map(variant -> () ->
                updateVariant.execute(new UpdateVariant.Command(ADMIN, variant.id(), Optional.empty(),
                    Optional.of(false)))).toList());

            assertThat(outcomes).filteredOn(UpdateVariant.Result.Updated.class::isInstance).hasSize(1);
            assertThat(outcomes).containsOnlyOnce(new UpdateVariant.Result.LastActivePricedVariant());
            assertThat(owner.queryForObject("select count(*) from variant where product_id = ? and active",
                Integer.class, product.id().value())).as("round %d", round).isOne();
        }
    }

    private ProductView create(String name) {
        var created = (CreateProduct.Result.Created) CurrentActor.callAs(ADMIN, () ->
            createProduct.execute(new CreateProduct.Command(ADMIN, details(name))));
        return created.product();
    }

    private ProductDetailsInput details(String name) {
        return new ProductDetailsInput(category, name, "", "Algodón", Season.SUMMER, Audience.WOMEN);
    }

    /** Starts every request at the same moment, each as the administrator in its own thread and transaction. */
    private static List<Object> runAtOnce(List<Callable<Object>> requests) throws Exception {
        var start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests.size());
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (Callable<Object> request : requests) {
                results.add(pool.submit(() -> {
                    start.await();
                    return CurrentActor.callAs(ADMIN, () -> {
                        try {
                            return request.call();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    });
                }));
            }
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }
}
