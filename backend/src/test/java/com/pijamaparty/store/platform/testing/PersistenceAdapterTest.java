package com.pijamaparty.store.platform.testing;

import com.pijamaparty.store.TestcontainersConfiguration;
import com.pijamaparty.store.platform.persistence.PersistenceConfiguration;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;

/**
 * JPA slice for the real side of a persistence contract test: Flyway migrations on a Testcontainers PostgreSQL
 * (never an embedded database), constructor injection, and every test rolled back. The slice does not scan
 * adapters, so the test must {@code @Import} the adapter under test, and it must override
 * {@link ContractTest#persistAndForget()} to flush and clear.
 *
 * <p>Not for concurrency or Row Level Security tests: other connections cannot see rows of the rolled-back test
 * transaction, and a transaction-local security context would last for the whole test. Those tests commit real
 * transactions and clean up explicitly (ARCHITECTURE.md section 10).
 *
 * <p>Like every test on {@link TestcontainersConfiguration}, it connects as {@code tienda_app} (no owner, no
 * BYPASSRLS) after Flyway migrated as {@code tienda_owner}, so policies apply as in production (RNF-27).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, PersistenceConfiguration.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public @interface PersistenceAdapterTest {
}
