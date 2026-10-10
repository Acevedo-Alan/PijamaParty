package com.pijamaparty.store.catalog.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * UC-06: builds a product's variants from sizes × colors. Generation is additive: it plans only the combinations the
 * product does not have, and the requested sizes and colors come first in the display order, followed by the
 * product's other ones in their previous order.
 */
public final class VariantMatrix {

    /** UC-06: per generation and per product. */
    public static final int MAX_SIZES = 20;
    public static final int MAX_COLORS = 20;

    private VariantMatrix() {
    }

    /** Validates the sizes and colors as the administrator typed them (trimmed, without repeats). */
    public static Parsed parse(List<String> sizes, List<String> colors) {
        Objects.requireNonNull(sizes, "sizes");
        Objects.requireNonNull(colors, "colors");
        Optional<List<Size>> parsedSizes = parseAll(sizes, Size::parse);
        if (parsedSizes.isEmpty()) {
            return new Parsed.Invalid(Problem.INVALID_SIZE);
        }
        Optional<List<Color>> parsedColors = parseAll(colors, Color::parse);
        if (parsedColors.isEmpty()) {
            return new Parsed.Invalid(Problem.INVALID_COLOR);
        }
        return check(parsedSizes.get(), parsedColors.get())
            .<Parsed>map(Parsed.Invalid::new)
            .orElseGet(() -> new Parsed.Valid(new Request(parsedSizes.get(), parsedColors.get())));
    }

    /**
     * The new display order and the missing combinations, given the product's current sizes and colors (in order) and
     * the combinations it has. Rejected when the product would end up with too many sizes or colors.
     */
    static Plan plan(List<Size> currentSizes, List<Color> currentColors, Set<Combination> existing, Request request) {
        List<Size> sizeOrder = requestedFirst(request.sizes(), currentSizes);
        List<Color> colorOrder = requestedFirst(request.colors(), currentColors);
        if (sizeOrder.size() > MAX_SIZES) {
            return new Plan.Rejected(Problem.TOO_MANY_SIZES);
        }
        if (colorOrder.size() > MAX_COLORS) {
            return new Plan.Rejected(Problem.TOO_MANY_COLORS);
        }
        List<Combination> missing = new ArrayList<>();
        for (Size size : request.sizes()) {
            for (Color color : request.colors()) {
                Combination combination = new Combination(size, color);
                if (!existing.contains(combination)) {
                    missing.add(combination);
                }
            }
        }
        return new Plan.Ready(sizeOrder, colorOrder, List.copyOf(missing));
    }

    private static <T> List<T> requestedFirst(List<T> requested, List<T> current) {
        Set<T> order = new LinkedHashSet<>(requested);
        order.addAll(current);
        return List.copyOf(order);
    }

    private static Optional<Problem> check(List<Size> sizes, List<Color> colors) {
        if (sizes.isEmpty()) {
            return Optional.of(Problem.NO_SIZES);
        }
        if (colors.isEmpty()) {
            return Optional.of(Problem.NO_COLORS);
        }
        if (sizes.size() > MAX_SIZES) {
            return Optional.of(Problem.TOO_MANY_SIZES);
        }
        if (colors.size() > MAX_COLORS) {
            return Optional.of(Problem.TOO_MANY_COLORS);
        }
        if (new HashSet<>(sizes).size() != sizes.size()) {
            return Optional.of(Problem.REPEATED_SIZES);
        }
        if (new HashSet<>(colors).size() != colors.size()) {
            return Optional.of(Problem.REPEATED_COLORS);
        }
        return Optional.empty();
    }

    private static <T> Optional<List<T>> parseAll(List<String> raw, Function<String, Optional<T>> parser) {
        List<T> parsed = new ArrayList<>(raw.size());
        for (String value : raw) {
            Optional<T> one = parser.apply(value);
            if (one.isEmpty()) {
                return Optional.empty();
            }
            parsed.add(one.get());
        }
        return Optional.of(parsed);
    }

    public enum Problem {
        /** A size is empty, longer than 20 characters or has control or invisible characters. */
        INVALID_SIZE,
        /** A color is empty, longer than 30 characters or has control or invisible characters. */
        INVALID_COLOR,
        NO_SIZES,
        NO_COLORS,
        REPEATED_SIZES,
        REPEATED_COLORS,
        /** More than 20 sizes in the request, or in the product once generated. */
        TOO_MANY_SIZES,
        /** More than 20 colors in the request, or in the product once generated. */
        TOO_MANY_COLORS
    }

    /** One generation: 1 to 20 sizes and 1 to 20 colors, without repeats, in the administrator's order. */
    public record Request(List<Size> sizes, List<Color> colors) {

        public Request {
            sizes = List.copyOf(sizes);
            colors = List.copyOf(colors);
            if (check(sizes, colors).isPresent()) {
                throw new IllegalArgumentException("not a valid variant matrix");
            }
        }
    }

    public sealed interface Parsed {

        record Valid(Request request) implements Parsed {}

        record Invalid(Problem problem) implements Parsed {}
    }

    record Combination(Size size, Color color) {}

    sealed interface Plan {

        record Ready(List<Size> sizeOrder, List<Color> colorOrder, List<Combination> missing) implements Plan {

            public Ready {
                sizeOrder = List.copyOf(sizeOrder);
                colorOrder = List.copyOf(colorOrder);
                missing = List.copyOf(missing);
            }
        }

        record Rejected(Problem problem) implements Plan {}
    }
}
