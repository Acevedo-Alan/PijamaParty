package com.pijamaparty.store.backoffice.adapter.in.web;

import com.pijamaparty.store.backoffice.application.GetWorkloadCounts;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** T-E8-05: the counts of the back-office menu. Only translates between HTTP and the use case. */
@RestController
@Tag(name = "Back office summary")
class WorkloadCountsAdminController {

    private final GetWorkloadCounts getWorkloadCounts;

    WorkloadCountsAdminController(GetWorkloadCounts getWorkloadCounts) {
        this.getWorkloadCounts = getWorkloadCounts;
    }

    @GetMapping("/api/admin/dashboard/counts")
    @Operation(summary = "The counts of the back-office menu: the PAID orders waiting to be prepared")
    @ApiResponse(responseCode = "200", description = "The counts", content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = WorkloadCountsResponse.class)))
    @ApiResponse(responseCode = "403", description = "Not the administrator",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<?> get() {
        return switch (getWorkloadCounts.execute(new GetWorkloadCounts.Query(CurrentActor.get().orElseThrow()))) {
            case GetWorkloadCounts.Result.Counted counted ->
                ResponseEntity.ok(new WorkloadCountsResponse(counted.paidOrders()));
            case GetWorkloadCounts.Result.Forbidden forbidden ->
                ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetail.forStatus(HttpStatus.FORBIDDEN));
        };
    }

    /** @param paidOrders orders PAID, waiting to be prepared: the badge on "Pedidos" */
    record WorkloadCountsResponse(long paidOrders) {}
}
