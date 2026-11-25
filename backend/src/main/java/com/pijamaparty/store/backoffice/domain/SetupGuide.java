package com.pijamaparty.store.backoffice.domain;

import com.pijamaparty.store.backoffice.domain.SetupFacts.Named;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * UC-46: the store setup guide of the summary, every item in order with its state. An omitted item stays omitted
 * whatever its data says, until it is shown again.
 */
public record SetupGuide(List<Entry> entries) {

    /** How many things an item names at most (the categories and the products); the rest are counted. */
    public static final int NAMED = 5;

    public SetupGuide {
        entries = List.copyOf(entries);
    }

    public enum State { DONE, PENDING, OMITTED }

    /**
     * One item.
     *
     * @param named what it names: categories or products while pending, nothing otherwise
     */
    public record Entry(SetupItem item, State state, Named named) {

        public Entry {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(named, "named");
        }
    }

    /** Decides each item's state from the store's data and the items the owner omitted. */
    public static SetupGuide from(SetupFacts facts, Set<SetupItem> omitted) {
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(omitted, "omitted");
        return new SetupGuide(Arrays.stream(SetupItem.values())
            .map(item -> entry(item, facts, omitted.contains(item) && item.omittable()))
            .toList());
    }

    private static Entry entry(SetupItem item, SetupFacts facts, boolean omitted) {
        if (omitted) {
            return new Entry(item, State.OMITTED, Named.NONE);
        }
        return switch (item) {
            case WHATSAPP -> done(item, facts.whatsApp());
            case EMAIL -> done(item, facts.email());
            case INSTAGRAM -> done(item, facts.instagram());
            case HOME_BANNER -> done(item, facts.homeBannerPublished());
            case SIZE_GUIDES -> naming(item, facts.categoriesWithoutSizeGuide());
            case PRODUCT_PHOTOS -> naming(item, facts.productsWithoutPhoto());
        };
    }

    private static Entry done(SetupItem item, boolean done) {
        return new Entry(item, done ? State.DONE : State.PENDING, Named.NONE);
    }

    private static Entry naming(SetupItem item, Named missing) {
        if (missing.isEmpty()) {
            return new Entry(item, State.DONE, Named.NONE);
        }
        List<SetupFacts.Thing> first = missing.first().stream().limit(NAMED).toList();
        return new Entry(item, State.PENDING, new Named(first, missing.total()));
    }

    /** "N de 6 listos": done and omitted items together. */
    public long ready() {
        return entries.stream().filter(entry -> entry.state() != State.PENDING).count();
    }
}
