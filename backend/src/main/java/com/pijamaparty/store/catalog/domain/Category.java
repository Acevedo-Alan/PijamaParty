package com.pijamaparty.store.catalog.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;

/**
 * A product category (UC-05). Its slug always follows its name, and deactivating it can be undone. Whether it can be
 * deleted depends on its products, which the category does not know: the database decides (V3 foreign key).
 */
public final class Category {

    private final CategoryId id;
    private CategoryName name;
    private Slug slug;
    private int position;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    private Category(CategoryId id, CategoryName name, Slug slug, int position, boolean active, Instant createdAt,
                     Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.slug = Objects.requireNonNull(slug, "slug");
        this.position = requireValidPosition(position);
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** UC-05: a new category is active and goes after the {@code existing} ones. */
    public static Category create(CategoryId id, CategoryName name, Collection<Category> existing, Instant now) {
        int last = existing.stream().mapToInt(Category::position).max().orElse(-1);
        return new Category(id, name, name.slug(), last + 1, true, now, now);
    }

    /** Rebuilds a stored category; only persistence adapters call it. */
    public static Category restore(CategoryId id, CategoryName name, Slug slug, int position, boolean active,
                                   Instant createdAt, Instant updatedAt) {
        return new Category(id, name, slug, position, active, createdAt, updatedAt);
    }

    /** UC-05: renaming regenerates the slug, so the URL always matches the name. */
    public void rename(CategoryName newName, Instant now) {
        Objects.requireNonNull(newName, "newName");
        if (!newName.equals(name)) {
            name = newName;
            slug = newName.slug();
            updatedAt = now;
        }
    }

    public void deactivate(Instant now) {
        if (active) {
            active = false;
            updatedAt = now;
        }
    }

    public void activate(Instant now) {
        if (!active) {
            active = true;
            updatedAt = now;
        }
    }

    /** Only {@link CategoryOrder} moves categories. Returns whether the position changed. */
    boolean moveTo(int newPosition, Instant now) {
        if (newPosition == position) {
            return false;
        }
        position = requireValidPosition(newPosition);
        updatedAt = now;
        return true;
    }

    public Category copy() {
        return new Category(id, name, slug, position, active, createdAt, updatedAt);
    }

    private static int requireValidPosition(int position) {
        if (position < 0) {
            throw new IllegalArgumentException("position must not be negative");
        }
        return position;
    }

    public CategoryId id() {
        return id;
    }

    public CategoryName name() {
        return name;
    }

    public Slug slug() {
        return slug;
    }

    public int position() {
        return position;
    }

    public boolean active() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
