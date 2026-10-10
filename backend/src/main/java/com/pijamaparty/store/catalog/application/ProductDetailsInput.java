package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Audience;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Description;
import com.pijamaparty.store.catalog.domain.Material;
import com.pijamaparty.store.catalog.domain.ProductDetails;
import com.pijamaparty.store.catalog.domain.ProductName;
import com.pijamaparty.store.catalog.domain.Season;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** The details of a product as the administrator typed them (UC-06); the use cases trim and validate them. */
public record ProductDetailsInput(CategoryId categoryId, String name, String description, String material,
                                  Season season, Audience audience) {

    /** Valid details, or every field that is not. */
    Parsed parse() {
        Optional<ProductName> parsedName = ProductName.parse(name);
        Optional<Description> parsedDescription = Description.parse(description);
        Optional<Material> parsedMaterial = Material.parse(material);
        Set<Field> invalid = EnumSet.noneOf(Field.class);
        if (categoryId == null) {
            invalid.add(Field.CATEGORY);
        }
        if (parsedName.isEmpty()) {
            invalid.add(Field.NAME);
        }
        if (parsedDescription.isEmpty()) {
            invalid.add(Field.DESCRIPTION);
        }
        if (parsedMaterial.isEmpty()) {
            invalid.add(Field.MATERIAL);
        }
        if (season == null) {
            invalid.add(Field.SEASON);
        }
        if (audience == null) {
            invalid.add(Field.AUDIENCE);
        }
        if (!invalid.isEmpty()) {
            return new Parsed.Invalid(Set.copyOf(invalid));
        }
        return new Parsed.Valid(new ProductDetails(categoryId, parsedName.get(), parsedDescription.get(),
            parsedMaterial.get(), season, audience));
    }

    public enum Field {
        CATEGORY,
        /** Empty, longer than 120 characters, on several lines or without letters or digits to build a slug from. */
        NAME,
        /** Longer than 5000 characters or with control or invisible characters other than line breaks. */
        DESCRIPTION,
        /** Empty, longer than 80 characters or on several lines. */
        MATERIAL,
        SEASON,
        AUDIENCE
    }

    sealed interface Parsed {

        record Valid(ProductDetails details) implements Parsed {}

        record Invalid(Set<Field> fields) implements Parsed {

            public Invalid {
                fields = Set.copyOf(fields);
            }
        }
    }
}
