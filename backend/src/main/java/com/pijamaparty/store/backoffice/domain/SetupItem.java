package com.pijamaparty.store.backoffice.domain;

/**
 * UC-46: the items of the store setup guide, in the order the summary shows them. Only what a store may do without
 * (Instagram, the banner) can be omitted; the others are what a working store needs, so they stay until done.
 */
public enum SetupItem {
    WHATSAPP(false),
    EMAIL(false),
    SIZE_GUIDES(false),
    PRODUCT_PHOTOS(false),
    INSTAGRAM(true),
    HOME_BANNER(true);

    private final boolean omittable;

    SetupItem(boolean omittable) {
        this.omittable = omittable;
    }

    public boolean omittable() {
        return omittable;
    }
}
