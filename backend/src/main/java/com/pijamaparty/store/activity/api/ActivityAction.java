package com.pijamaparty.store.activity.api;

/** UC-37: what was done, each in its area. The table of UC-37 lists them; the frontend translates each name. */
public enum ActivityAction {
    CATEGORY_CREATED(ActivityArea.CATALOG),
    CATEGORY_RENAMED(ActivityArea.CATALOG),
    CATEGORIES_REORDERED(ActivityArea.CATALOG),
    CATEGORY_DEACTIVATED(ActivityArea.CATALOG),
    CATEGORY_ACTIVATED(ActivityArea.CATALOG),
    CATEGORY_DELETED(ActivityArea.CATALOG),
    PRODUCT_CREATED(ActivityArea.CATALOG),
    PRODUCT_UPDATED(ActivityArea.CATALOG),
    PRODUCT_PUBLISHED(ActivityArea.CATALOG),
    PRODUCT_HIDDEN(ActivityArea.CATALOG),
    VARIANTS_GENERATED(ActivityArea.CATALOG),
    VARIANT_UPDATED(ActivityArea.CATALOG),
    PRODUCT_IMAGE_UPLOADED(ActivityArea.CATALOG),
    PRODUCT_IMAGES_REORDERED(ActivityArea.CATALOG),
    PRODUCT_IMAGE_DELETED(ActivityArea.CATALOG),
    SIZE_GUIDE_SAVED(ActivityArea.CATALOG),

    STOCK_ADJUSTED(ActivityArea.STOCK),

    ORDER_STATUS_CHANGED(ActivityArea.ORDERS),
    TRACKING_NUMBER_CORRECTED(ActivityArea.ORDERS),
    WITHDRAWAL_RESOLVED(ActivityArea.ORDERS),

    COUPON_CREATED(ActivityArea.PROMOTIONS),
    COUPON_UPDATED(ActivityArea.PROMOTIONS),
    COUPON_ACTIVATED(ActivityArea.PROMOTIONS),
    COUPON_DEACTIVATED(ActivityArea.PROMOTIONS),
    COUPON_DELETED(ActivityArea.PROMOTIONS),

    REVIEW_APPROVED(ActivityArea.REVIEWS),
    REVIEW_REJECTED(ActivityArea.REVIEWS),
    REVIEW_DELETED(ActivityArea.REVIEWS),

    SHIPPING_ZONES_CHANGED(ActivityArea.SHIPPING),
    FREE_SHIPPING_THRESHOLD_CHANGED(ActivityArea.SHIPPING),

    HOME_BANNER_UPDATED(ActivityArea.CONTENT),
    HOME_BANNER_PUBLISHED(ActivityArea.CONTENT),
    HOME_BANNER_HIDDEN(ActivityArea.CONTENT),
    HOME_BANNER_PHOTO_REPLACED(ActivityArea.CONTENT),

    STORE_SETTINGS_CHANGED(ActivityArea.SETTINGS),

    CUSTOMER_VIEWED(ActivityArea.CUSTOMERS),

    ADMIN_SIGNED_IN(ActivityArea.ACCOUNT),
    SECOND_FACTOR_ENROLLED(ActivityArea.ACCOUNT),
    RECOVERY_CODE_USED(ActivityArea.ACCOUNT);

    private final ActivityArea area;

    ActivityAction(ActivityArea area) {
        this.area = area;
    }

    public ActivityArea area() {
        return area;
    }
}
