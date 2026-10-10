package com.pijamaparty.store.platform.testing;

import org.junit.jupiter.api.BeforeEach;

/**
 * Base of every port contract test (Liskov Substitution, ARCHITECTURE.md section 5). A {@code <Port>ContractTest}
 * extends this class and states the behavior of the port once; every implementation, the {@code InMemory<Port>}
 * fake and the real adapter, runs it through a subclass that only provides {@link #createSubject()}.
 *
 * <p>Read and write ports are separate (Interface Segregation), so the contract of a pair takes a subject that
 * implements both: it arranges through the write port, calls {@link #persistAndForget()}, then asserts through the
 * read port.
 *
 * <pre>{@code
 * abstract class CartStoreContractTest<T extends LoadCart & SaveCart> extends ContractTest<T> {
 *     @Test void givenASavedCart_whenLoadingIt_thenItHasTheSameItems() {
 *         subject.save(cart);
 *         persistAndForget();
 *         assertThat(subject.load(cart.ref())).contains(cart);
 *     }
 * }
 * class InMemoryCartStoreContractTest extends CartStoreContractTest<InMemoryCartStore> { ... }
 *
 * @PersistenceAdapterTest @Import(JpaCartStore.class)
 * class JpaCartStoreContractTest extends CartStoreContractTest<JpaCartStore> {
 *     private final JpaCartStore adapter;
 *     private final TestEntityManager entityManager;
 *     JpaCartStoreContractTest(JpaCartStore adapter, TestEntityManager entityManager) { ... }
 *     protected JpaCartStore createSubject() { return adapter; }
 *     protected void persistAndForget() { entityManager.flush(); entityManager.clear(); }
 * }
 * }</pre>
 *
 * @param <P> the port (or pair of ports) under test
 */
public abstract class ContractTest<P> {

    /** A fresh implementation before every test. */
    protected P subject;

    /** Returns the implementation under test. Called before every test, after Spring injection if any. */
    protected abstract P createSubject();

    /**
     * Contracts call this between arranging and asserting. Fakes need nothing. JPA adapters must flush and clear
     * the persistence context, so the assertion reads what PostgreSQL stored (mappings, constraints, policies)
     * instead of Hibernate's first-level cache.
     */
    protected void persistAndForget() {
    }

    @BeforeEach
    void createSubjectUnderTest() {
        subject = createSubject();
    }
}
