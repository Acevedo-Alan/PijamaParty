package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.application.ListWithdrawals.Query;
import com.pijamaparty.store.ordering.application.ListWithdrawals.Result;
import com.pijamaparty.store.ordering.application.SaveWithdrawals.NewWithdrawal;
import com.pijamaparty.store.ordering.domain.OrderStatus;
import com.pijamaparty.store.ordering.domain.WithdrawalCode;
import com.pijamaparty.store.ordering.domain.WithdrawalStatus;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ListWithdrawalsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T18:00:00Z");
    private static final Actor ADMIN = Actor.admin(UserId.of(UUID.randomUUID()));

    private final InMemoryWithdrawalStore store = new InMemoryWithdrawalStore();
    private final ListWithdrawalsService service = new ListWithdrawalsService(store);

    @Test
    void givenAPaidOrderWhoseFormWasSent_whenTheAdministratorLists_thenTheRequestAppearsWithItsOrder() {
        UUID id = requested(0, false);

        assertThat(service.execute(new Query(ADMIN, Optional.empty(), 0)))
            .isInstanceOfSatisfying(Result.Listed.class, listed -> {
                assertThat(listed.withdrawals()).singleElement().satisfies(withdrawal -> {
                    assertThat(withdrawal.id()).isEqualTo(id);
                    assertThat(withdrawal.orderNumber()).isEqualTo(1001);
                    assertThat(withdrawal.orderStatus()).isEqualTo(OrderStatus.PAID);
                    assertThat(withdrawal.status()).isEqualTo(WithdrawalStatus.RECEIVED);
                });
                assertThat(listed.hasMore()).isFalse();
            });
    }

    @Test
    void givenReceivedAndResolvedRequests_whenFilteredByReceived_thenOnlyTheActiveOnesAppearWithTheirTermMark() {
        UUID resolved = requested(0, false);
        UUID received = requested(1, true);
        store.resolve(resolved, NOW.plusSeconds(3600));

        assertThat(service.execute(new Query(ADMIN, Optional.of(WithdrawalStatus.RECEIVED), 0)))
            .isInstanceOfSatisfying(Result.Listed.class, listed -> assertThat(listed.withdrawals())
                .singleElement().satisfies(withdrawal -> {
                    assertThat(withdrawal.id()).isEqualTo(received);
                    assertThat(withdrawal.outOfTerm()).isTrue();
                }));
    }

    @Test
    void givenMoreRequestsThanAPage_whenListed_thenFiftyComeNewestFirstAndTheRestOnTheNextPage() {
        List<UUID> requested = IntStream.range(0, 51).mapToObj(minutes -> requested(minutes, false)).toList();

        Result first = service.execute(new Query(ADMIN, Optional.empty(), 0));
        Result second = service.execute(new Query(ADMIN, Optional.empty(), 1));

        assertThat(first).isInstanceOfSatisfying(Result.Listed.class, listed -> {
            assertThat(listed.withdrawals()).hasSize(50).first().extracting(WithdrawalSummary::id)
                .isEqualTo(requested.get(50));
            assertThat(listed.hasMore()).isTrue();
        });
        assertThat(second).isInstanceOfSatisfying(Result.Listed.class, listed -> {
            assertThat(listed.withdrawals()).extracting(WithdrawalSummary::id).containsExactly(requested.get(0));
            assertThat(listed.hasMore()).isFalse();
        });
    }

    @Test
    void givenSomeoneButTheAdministrator_whenListing_thenItIsForbidden() {
        assertThat(service.execute(new Query(Actor.system(), Optional.empty(), 0))).isEqualTo(new Result.Forbidden());
    }

    /** A request for a new paid order, sent {@code minutes} after NOW. */
    private UUID requested(int minutes, boolean outOfTerm) {
        var order = store.place(OrderStatus.PAID, NOW.minusSeconds(86_400), Optional.empty());
        UUID id = UUID.randomUUID();
        store.insert(new NewWithdrawal(id, order.id(), codeNumber(minutes), outOfTerm,
            NOW.plusSeconds(60L * minutes)));
        return id;
    }

    /** A distinct code per {@code n}: its digits in base 31, written with the code's alphabet. */
    private static WithdrawalCode codeNumber(int n) {
        var code = new StringBuilder();
        for (int i = 0, rest = n; i < WithdrawalCode.LENGTH; i++, rest /= 31) {
            code.insert(0, WithdrawalCode.ALPHABET.charAt(rest % 31));
        }
        return new WithdrawalCode(WithdrawalCode.PREFIX + code);
    }
}
