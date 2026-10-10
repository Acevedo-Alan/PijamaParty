package com.pijamaparty.store.platform.testing;

import com.pijamaparty.store.TestcontainersConfiguration;
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
 * <p>TODO(T-E0-13): connect as {@code tienda_app} and migrate as {@code tienda_owner}. Until then the tests use the
 * container's superuser, which bypasses RLS (RNF-27, RNF-29.2).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public @interface PersistenceAdapterTest {
}
