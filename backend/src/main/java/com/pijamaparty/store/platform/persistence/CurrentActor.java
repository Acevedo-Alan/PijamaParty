package com.pijamaparty.store.platform.persistence;

import com.pijamaparty.store.platform.domain.Actor;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The actor of the current thread, read by {@link RlsTransactionManager} when a transaction begins (RNF-27). The web
 * layer binds it for each request; background work (webhooks, scheduled jobs, email sending, authentication) runs
 * its block through {@link #callAsSystem(Supplier)}. Unbound means no context: protected tables return no rows.
 */
public final class CurrentActor {

    private static final ThreadLocal<Actor> ACTOR = new ThreadLocal<>();

    private CurrentActor() {
    }

    public static Optional<Actor> get() {
        return Optional.ofNullable(ACTOR.get());
    }

    /**
     * Binds {@code actor} until the returned binding is closed, which restores whatever was bound before:
     * {@code try (var binding = CurrentActor.bind(actor)) { ... }}.
     *
     * <p>The database context is fixed when a transaction begins, so switching actor inside an active transaction
     * would change what the use case believes but not what RLS enforces. That is refused: wrap the transaction
     * boundary instead ({@code callAsSystem(() -> useCase.execute(...))}).
     *
     * @throws IllegalStateException when called inside an active transaction with a different actor
     */
    public static Binding bind(Actor actor) {
        Objects.requireNonNull(actor, "actor");
        Actor previous = ACTOR.get();
        if (TransactionSynchronizationManager.isActualTransactionActive() && !actor.equals(previous)) {
            throw new IllegalStateException(
                "Cannot switch actor inside an active transaction: the RLS context was set when it began");
        }
        ACTOR.set(actor);
        return new Binding(previous);
    }

    public static <T> T callAs(Actor actor, Supplier<T> block) {
        try (Binding binding = bind(actor)) {
            return block.get();
        }
    }

    public static void runAs(Actor actor, Runnable block) {
        try (Binding binding = bind(actor)) {
            block.run();
        }
    }

    /** For webhooks, jobs, email sending and authentication: the {@code SYSTEM} role of RNF-27. */
    public static <T> T callAsSystem(Supplier<T> block) {
        return callAs(Actor.system(), block);
    }

    public static void runAsSystem(Runnable block) {
        runAs(Actor.system(), block);
    }

    /** Restores the previous actor (or none) when closed. */
    public static final class Binding implements AutoCloseable {

        private final Actor previous;

        private Binding(Actor previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                ACTOR.remove();
            } else {
                ACTOR.set(previous);
            }
        }
    }
}
