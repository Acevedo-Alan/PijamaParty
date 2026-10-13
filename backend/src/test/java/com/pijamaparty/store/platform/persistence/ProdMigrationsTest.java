package com.pijamaparty.store.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayConnectionDetails;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import({TestcontainersConfiguration.class, ProdMigrationsTest.MigrateContainerStep.class})
@ActiveProfiles("prod")
// T-E3-06, T-E3-07: the production profile does not start without them
@TestPropertySource(properties = {"PUBLIC_URL=https://pijamaparty.test", "MERCADOPAGO_ACCESS_TOKEN=TEST-token",
    "MERCADOPAGO_WEBHOOK_SECRET=test-secret", "RESEND_API_KEY=re_test", "MAIL_FROM=pedidos@pijamaparty.test",
    "NOTIFICATIONS_ADMIN_EMAIL=admin@pijamaparty.test"})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ProdMigrationsTest {

    private final ApplicationContext context;

    ProdMigrationsTest(ApplicationContext context) {
        this.context = context;
    }

    // T-E0-06: in production the one-shot migrate container runs Flyway as tienda_owner; the API never migrates.
    @Test
    void givenTheProdProfile_whenTheContextStarts_thenTheApplicationDoesNotRunFlyway() {
        assertThat(context.getBeanNamesForType(Flyway.class)).isEmpty();
    }

    /** Stands in for the migrate container: migrates as the owner before JPA validates the schema. */
    @TestConfiguration(proxyBeanMethods = false)
    static class MigrateContainerStep {

        @Bean
        MigrateResult migrateContainer(FlywayConnectionDetails owner) {
            return Flyway.configure()
                .dataSource(owner.getJdbcUrl(), owner.getUsername(), owner.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        }

        @Bean
        static EntityManagerFactoryDependsOnPostProcessor jpaAfterMigrateContainer() {
            return new EntityManagerFactoryDependsOnPostProcessor("migrateContainer");
        }
    }
}
