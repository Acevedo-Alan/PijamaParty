package com.pijamaparty.store.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.platform.testing.ApiTest;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * UC-37 (T-E8-14): "every write of the back office" is recorded. Every POST, PUT, PATCH and DELETE under
 * {@code /api/admin} that the application maps must have a row in {@link #WRITES}: the actions its use case records,
 * or why it records none. A new endpoint without a row fails here, so the rule keeps holding after E8a. Every action
 * of {@link ActivityAction} must come from some endpoint, and {@link ActivityPersonalDataTest} runs each one through
 * its use case on PostgreSQL; the use-case and API tests of each endpoint assert the entry itself.
 */
@ApiTest
class AdminWriteActivityTest {

    private static final Set<RequestMethod> WRITE_METHODS = EnumSet.of(RequestMethod.POST, RequestMethod.PUT,
        RequestMethod.PATCH, RequestMethod.DELETE);

    /** What a back-office write leaves in the activity log. */
    private sealed interface Recording {}

    private record Records(Set<ActivityAction> actions) implements Recording {

        Records(ActivityAction first, ActivityAction... more) {
            this(EnumSet.of(first, more));
        }
    }

    /** A write that changes nothing of the store, so it has nothing to record. */
    private record Exempt(String reason) implements Recording {}

    private static final Map<String, Recording> WRITES = Map.ofEntries(
        // UC-04: the sign-in is recorded when the second factor completes it, not at the password step.
        Map.entry("POST /api/admin/auth/login", new Exempt("the password step alone signs nobody in")),
        Map.entry("POST /api/admin/auth/second-factor",
            new Records(ActivityAction.ADMIN_SIGNED_IN, ActivityAction.RECOVERY_CODE_USED)),
        Map.entry("POST /api/admin/auth/enrollment",
            new Records(ActivityAction.ADMIN_SIGNED_IN, ActivityAction.SECOND_FACTOR_ENROLLED)),
        Map.entry("POST /api/admin/auth/logout", new Exempt("ends the session; changes nothing of the store")),
        // UC-05 and RNF-31.5.
        Map.entry("POST /api/admin/catalog/categories", new Records(ActivityAction.CATEGORY_CREATED)),
        Map.entry("PATCH /api/admin/catalog/categories/{id}", new Records(ActivityAction.CATEGORY_RENAMED)),
        Map.entry("PUT /api/admin/catalog/categories/order", new Records(ActivityAction.CATEGORIES_REORDERED)),
        Map.entry("POST /api/admin/catalog/categories/{id}/deactivate",
            new Records(ActivityAction.CATEGORY_DEACTIVATED)),
        Map.entry("POST /api/admin/catalog/categories/{id}/activate", new Records(ActivityAction.CATEGORY_ACTIVATED)),
        Map.entry("DELETE /api/admin/catalog/categories/{id}", new Records(ActivityAction.CATEGORY_DELETED)),
        Map.entry("PUT /api/admin/catalog/categories/{id}/size-guide", new Records(ActivityAction.SIZE_GUIDE_SAVED)),
        Map.entry("DELETE /api/admin/catalog/categories/{id}/size-guide",
            new Records(ActivityAction.SIZE_GUIDE_DELETED)),
        // UC-06.
        Map.entry("POST /api/admin/catalog/products", new Records(ActivityAction.PRODUCT_CREATED)),
        Map.entry("PUT /api/admin/catalog/products/{id}", new Records(ActivityAction.PRODUCT_UPDATED)),
        Map.entry("POST /api/admin/catalog/products/{id}/variants/generate",
            new Records(ActivityAction.VARIANTS_GENERATED)),
        Map.entry("POST /api/admin/catalog/products/{id}/publish", new Records(ActivityAction.PRODUCT_PUBLISHED)),
        Map.entry("POST /api/admin/catalog/products/{id}/hide", new Records(ActivityAction.PRODUCT_HIDDEN)),
        Map.entry("PATCH /api/admin/catalog/variants/{id}", new Records(ActivityAction.VARIANT_UPDATED)),
        Map.entry("POST /api/admin/catalog/products/{id}/images", new Records(ActivityAction.PRODUCT_IMAGE_UPLOADED)),
        Map.entry("PUT /api/admin/catalog/products/{id}/images/order",
            new Records(ActivityAction.PRODUCT_IMAGES_REORDERED)),
        Map.entry("DELETE /api/admin/catalog/products/{id}/images/{imageId}",
            new Records(ActivityAction.PRODUCT_IMAGE_DELETED)),
        // UC-32.
        Map.entry("PUT /api/admin/home/banner", new Records(ActivityAction.HOME_BANNER_UPDATED)),
        Map.entry("POST /api/admin/home/banner/publish", new Records(ActivityAction.HOME_BANNER_PUBLISHED)),
        Map.entry("POST /api/admin/home/banner/hide", new Records(ActivityAction.HOME_BANNER_HIDDEN)),
        Map.entry("POST /api/admin/home/banner/photo", new Records(ActivityAction.HOME_BANNER_PHOTO_REPLACED)),
        // UC-07.
        Map.entry("POST /api/admin/stock-adjustments", new Records(ActivityAction.STOCK_ADJUSTED)),
        // UC-18 and UC-29.
        Map.entry("POST /api/admin/orders/{id}/status", new Records(ActivityAction.ORDER_STATUS_CHANGED)),
        Map.entry("PUT /api/admin/orders/{id}/shipment-tracking-number",
            new Records(ActivityAction.TRACKING_NUMBER_CORRECTED)),
        Map.entry("POST /api/admin/withdrawals/{id}/resolve", new Records(ActivityAction.WITHDRAWAL_RESOLVED)),
        // UC-11 and UC-23.
        Map.entry("POST /api/admin/coupons", new Records(ActivityAction.COUPON_CREATED)),
        Map.entry("PUT /api/admin/coupons/{id}", new Records(ActivityAction.COUPON_UPDATED)),
        Map.entry("POST /api/admin/coupons/{id}/activate", new Records(ActivityAction.COUPON_ACTIVATED)),
        Map.entry("POST /api/admin/coupons/{id}/deactivate", new Records(ActivityAction.COUPON_DEACTIVATED)),
        Map.entry("DELETE /api/admin/coupons/{id}", new Records(ActivityAction.COUPON_DELETED)),
        // UC-25.
        Map.entry("POST /api/admin/reviews/{id}/approve", new Records(ActivityAction.REVIEW_APPROVED)),
        Map.entry("POST /api/admin/reviews/{id}/reject", new Records(ActivityAction.REVIEW_REJECTED)),
        Map.entry("DELETE /api/admin/reviews/{id}", new Records(ActivityAction.REVIEW_DELETED)),
        // UC-12 and UC-36.
        Map.entry("PUT /api/admin/shipping/zones", new Records(ActivityAction.SHIPPING_ZONES_CHANGED)),
        Map.entry("PUT /api/admin/shipping/free-shipping",
            new Records(ActivityAction.FREE_SHIPPING_THRESHOLD_CHANGED)),
        Map.entry("PUT /api/admin/store/settings", new Records(ActivityAction.STORE_SETTINGS_CHANGED)));

    /** The reads that record too: opening a customer's profile (UC-34). */
    private static final Map<String, ActivityAction> RECORDED_READS = Map.of(
        "GET /api/admin/customers/{id}", ActivityAction.CUSTOMER_VIEWED);

    private final RequestMappingHandlerMapping mappings;

    AdminWriteActivityTest(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings) {
        this.mappings = mappings;
    }

    /** "METHOD /pattern" of every back-office endpoint the application maps with one of {@code methods}. */
    private Set<String> adminEndpoints(Set<RequestMethod> methods) {
        var endpoints = new TreeSet<String>();
        mappings.getHandlerMethods().keySet().forEach(info -> info.getPatternValues().stream()
            .filter(pattern -> pattern.startsWith("/api/admin/") || pattern.equals("/api/admin"))
            .forEach(pattern -> info.getMethodsCondition().getMethods().stream()
                .filter(methods::contains)
                .forEach(method -> endpoints.add(method + " " + pattern))));
        return endpoints;
    }

    @Test
    void givenTheMappedBackOfficeEndpoints_whenComparedWithTheTable_thenEveryWriteHasItsRowAndNoRowIsStale() {
        Set<String> mapped = adminEndpoints(WRITE_METHODS);

        assertThat(mapped)
            .as("every back-office write records its activity (UC-37): add a row to WRITES for each new endpoint")
            .containsExactlyInAnyOrderElementsOf(WRITES.keySet());
        assertThat(adminEndpoints(EnumSet.of(RequestMethod.GET))).containsAll(RECORDED_READS.keySet());
    }

    @Test
    void givenEveryActivityAction_whenLookedUp_thenSomeEndpointRecordsIt() {
        Set<ActivityAction> recorded = Stream.concat(
                WRITES.values().stream().flatMap(recording -> recording instanceof Records records
                    ? records.actions().stream() : Stream.empty()),
                RECORDED_READS.values().stream())
            .collect(() -> EnumSet.noneOf(ActivityAction.class), Set::add, Set::addAll);

        assertThat(recorded).containsExactlyInAnyOrderElementsOf(Arrays.asList(ActivityAction.values()));
    }

    @Test
    void givenTheExemptWrites_whenListed_thenEachSaysWhyItRecordsNothing() {
        assertThat(WRITES.values()).filteredOn(Exempt.class::isInstance).map(Exempt.class::cast)
            .allSatisfy(exempt -> assertThat(exempt.reason()).isNotBlank());
    }
}
