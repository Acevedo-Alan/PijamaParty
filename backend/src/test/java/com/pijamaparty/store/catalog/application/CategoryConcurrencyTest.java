package com.pijamaparty.store.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** UC-05 on PostgreSQL with committed transactions: requests that race keep the rules of the use case. */
@ApiTest
class CategoryConcurrencyTest {

    private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));
    private static final int REQUESTS = 4;

    private final CreateCategory createCategory;
    private final RenameCategory renameCategory;
    private final DeactivateCategory deactivateCategory;
    private final DeleteCategory deleteCategory;
    private final JdbcTemplate owner;

    CategoryConcurrencyTest(CreateCategory createCategory, RenameCategory renameCategory,
                            DeactivateCategory deactivateCategory, DeleteCategory deleteCategory,
                            PostgreSQLContainer<?> postgres) {
        this.createCategory = createCategory;
        this.renameCategory = renameCategory;
        this.deactivateCategory = deactivateCategory;
        this.deleteCategory = deleteCategory;
        this.owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), "tienda_owner",
            TestcontainersConfiguration.OWNER_PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        owner.execute("truncate product_image, variant, product, category");
    }

    @Test
    void givenSeveralRequestsCreatingTheSameSlugAtOnce_whenTheyRun_thenOneIsCreatedAndTheOthersAreRejected()
        throws Exception {
        List<Callable<Object>> requests = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            String name = i % 2 == 0 ? "Pijamas de Niñas" : "PIJAMAS DE NIÑAS";
            requests.add(() -> createCategory.execute(new CreateCategory.Command(ADMIN, name)));
        }

        List<Object> outcomes = runAtOnce(requests);

        assertThat(outcomes).filteredOn(CreateCategory.Result.Created.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(CreateCategory.Result.SlugAlreadyUsed.class::isInstance)
            .hasSize(REQUESTS - 1);
        assertThat(categoryCount()).isOne();
    }

    @Test
    void givenTwoCategoriesRenamedToTheSameSlugAtOnce_whenTheyRun_thenOneIsRenamedAndTheOtherIsRejected()
        throws Exception {
        CategoryId first = create("Invierno");
        CategoryId second = create("Otoño");

        List<Object> outcomes = runAtOnce(List.of(
            () -> renameCategory.execute(new RenameCategory.Command(ADMIN, first, "Verano")),
            () -> renameCategory.execute(new RenameCategory.Command(ADMIN, second, "VERANO"))));

        assertThat(outcomes).filteredOn(RenameCategory.Result.Renamed.class::isInstance).hasSize(1);
        assertThat(outcomes).containsOnlyOnce(new RenameCategory.Result.SlugAlreadyUsed("verano"));
        assertThat(owner.queryForObject("select count(*) from category where slug = 'verano'", Integer.class))
            .isOne();
    }

    @Test
    void givenACategoryDeletedAndDeactivatedAtOnce_whenTheyRun_thenItIsGoneAndNeverBroughtBack() throws Exception {
        for (int round = 0; round < 5; round++) {
            CategoryId id = create("Verano");

            List<Object> outcomes = runAtOnce(List.of(
                () -> deleteCategory.execute(new DeleteCategory.Command(ADMIN, id)),
                () -> deactivateCategory.execute(new DeactivateCategory.Command(ADMIN, id))));

            assertThat(outcomes.get(0)).isEqualTo(new DeleteCategory.Result.Deleted());
            assertThat(outcomes.get(1)).isInstanceOfAny(DeactivateCategory.Result.Deactivated.class,
                DeactivateCategory.Result.NotFound.class);
            assertThat(categoryCount()).as("round %d", round).isZero();
        }
    }

    private CategoryId create(String name) {
        var created = (CreateCategory.Result.Created) CurrentActor.callAs(ADMIN, () ->
            createCategory.execute(new CreateCategory.Command(ADMIN, name)));
        return created.category().id();
    }

    private Integer categoryCount() {
        return owner.queryForObject("select count(*) from category", Integer.class);
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
