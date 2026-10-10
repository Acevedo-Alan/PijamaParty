package com.pijamaparty.store.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ActorFilterTest {

    @Test
    void givenNoResolverRecognizesTheRequest_whenItIsServed_thenTheActorIsAnonymousWithoutTokens() throws Exception {
        Actor seen = serve(new ActorFilter(List.of()));

        assertThat(seen.role()).isEqualTo(Role.ANONYMOUS);
        assertThat(seen.cartToken()).isEmpty();
        assertThat(seen.trackingToken()).isEmpty();
    }

    @Test
    void givenResolvers_whenTheFirstRecognizesTheRequest_thenItsActorIsBound() throws Exception {
        ActorResolver none = request -> Optional.empty();
        ActorResolver system = request -> Optional.of(Actor.system());

        assertThat(serve(new ActorFilter(List.of(none, system))).role()).isEqualTo(Role.SYSTEM);
    }

    @Test
    void givenARequestThatFails_whenItEnds_thenThePooledThreadKeepsNoActor() {
        var filter = new ActorFilter(List.of(request -> Optional.of(Actor.system())));

        try {
            filter.doFilter(new MockHttpServletRequest("GET", "/api/x"), new MockHttpServletResponse(),
                (request, response) -> {
                    throw new IllegalStateException("boom");
                });
        } catch (Exception expected) {
            // the failure itself is not under test
        }

        assertThat(CurrentActor.get()).isEmpty();
    }

    private static Actor serve(ActorFilter filter) throws Exception {
        var seen = new AtomicReference<Actor>();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/x"), new MockHttpServletResponse(),
            (request, response) -> seen.set(CurrentActor.get().orElseThrow()));
        assertThat(CurrentActor.get()).as("unbound after the request").isEmpty();
        return seen.get();
    }
}
