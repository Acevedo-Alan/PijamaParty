package com.pijamaparty.store.payments.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.application.ConfirmPayment;
import com.pijamaparty.store.payments.application.ReconcileCheckouts;
import com.pijamaparty.store.platform.domain.Money;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * UC-15 (T-E3b-03): what the reconciliation logs. Approvals, rejections and amount mismatches with the provider, the
 * checkout id and the order number only (R-05); the outcomes that change nothing are not logged.
 */
@ExtendWith(OutputCaptureExtension.class)
class CheckoutReconciliationTest {

    private static final PaymentProvider MODO = PaymentProvider.MODO;

    @Test
    void givenReconciledCheckouts_whenTheJobRuns_thenOnlyTheirOutcomesWithTheOrderNumberAreLogged(
        CapturedOutput output) {
        var job = new CheckoutReconciliation(() -> new ReconcileCheckouts.Result(List.of(
            new ReconcileCheckouts.Reconciled(MODO, "pr-approved", new ConfirmPayment.Result.Approved(1001)),
            new ReconcileCheckouts.Reconciled(MODO, "pr-rejected", new ConfirmPayment.Result.Rejected(1002)),
            new ReconcileCheckouts.Reconciled(MODO, "pr-mismatch",
                new ConfirmPayment.Result.AmountMismatch(1003, Money.of(2_100_050), Money.of(2_000_000))),
            new ReconcileCheckouts.Reconciled(MODO, "pr-unpaid", new ConfirmPayment.Result.NotFoundAtProvider()),
            new ReconcileCheckouts.Reconciled(MODO, "pr-down", new ConfirmPayment.Result.ProviderUnavailable()))));

        job.run();

        assertThat(output).contains("Reconciliation: MODO checkout pr-approved approved order 1001")
            .contains("Reconciliation: MODO checkout pr-rejected of order 1002 was rejected")
            .contains("Reconciliation: MODO checkout pr-mismatch of order 1003 was approved for ARS 20000.00 instead "
                + "of ARS 21000.50: the order is not paid")
            .doesNotContain("pr-unpaid")
            .doesNotContain("pr-down");
    }
}
