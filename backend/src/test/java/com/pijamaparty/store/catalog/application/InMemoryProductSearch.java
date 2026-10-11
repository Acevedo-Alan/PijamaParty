package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.CategoryId;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.ProductFilter;
import com.pijamaparty.store.catalog.domain.ProductId;
import com.pijamaparty.store.catalog.domain.ProductSort;
import com.pijamaparty.store.catalog.domain.SearchText;
import com.pijamaparty.store.catalog.domain.Variant;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Fake of {@link ProductSearchQuery} and {@link SearchVectors} over the listing fake. It imitates PostgreSQL's spanish
 * configuration closely enough for the contract: accents and case ignored, a few stop words, a crude stemmer (a final
 * "s", then a final vowel) and weights A 1.0, B 0.4 and C 0.2. Every word must match; operators are not understood.
 */
public class InMemoryProductSearch implements ProductSearchQuery, SearchVectors {

    private static final Set<String> STOP_WORDS =
        Set.of("de", "del", "la", "las", "el", "los", "y", "o", "a", "en", "con", "para", "por", "un", "una");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}\\p{N}]+");
    private static final double NAME = 1.0;
    private static final double CATEGORY_AND_MATERIAL = 0.4;
    private static final double DESCRIPTION_AND_COLORS = 0.2;

    private final LoadCategories categories;
    private final InMemoryProductStore products;
    private final InMemoryProductListing listing;
    /** The vector of each product as last refreshed: lexeme to its highest weight. */
    private final Map<ProductId, Map<String, Double>> vectors = new ConcurrentHashMap<>();

    public InMemoryProductSearch(LoadCategories categories, InMemoryProductStore products,
                                 InMemoryProductListing listing) {
        this.categories = categories;
        this.products = products;
        this.listing = listing;
    }

    @Override
    public List<ProductListingItem> search(SearchText text, ProductFilter filter, Optional<ProductSort> sort,
                                           int offset, int limit) {
        List<String> terms = terms(text.words());
        Stream<ProductListingItem> matching = listing.list(filter, sort.orElse(ProductSort.NEWEST), 0, Integer.MAX_VALUE)
            .stream()
            .filter(item -> rank(item.slug(), terms, false) > 0);
        if (sort.isEmpty()) {
            // Stable: ties keep the newest-first order of the listing.
            matching = matching.sorted(Comparator.comparingDouble(
                (ProductListingItem item) -> rank(item.slug(), terms, false)).reversed());
        }
        return matching.skip(offset).limit(limit).toList();
    }

    @Override
    public List<ProductSuggestion> suggest(SearchText text, int limit) {
        List<String> words = text.words();
        if (words.isEmpty()) {
            return List.of();
        }
        List<String> terms = terms(words);
        return listing.list(ProductFilter.none(), ProductSort.NEWEST, 0, Integer.MAX_VALUE).stream()
            .filter(item -> rank(item.slug(), terms, isPrefix(words)) > 0)
            .sorted(Comparator.comparingDouble(
                (ProductListingItem item) -> rank(item.slug(), terms, isPrefix(words))).reversed())
            .limit(limit)
            .map(item -> new ProductSuggestion(item.slug(), item.name(), item.image()))
            .toList();
    }

    @Override
    public void refreshProduct(ProductId product) {
        products.findById(product).ifPresent(this::index);
    }

    @Override
    public void refreshCategory(CategoryId category) {
        products.all().stream().filter(product -> product.details().categoryId().equals(category))
            .forEach(this::index);
    }

    private void index(Product product) {
        Map<String, Double> vector = new HashMap<>();
        add(vector, product.details().name().value(), NAME);
        categories.findById(product.details().categoryId()).map(Category::name)
            .ifPresent(name -> add(vector, name.value(), CATEGORY_AND_MATERIAL));
        add(vector, product.details().material().value(), CATEGORY_AND_MATERIAL);
        add(vector, product.details().description().value(), DESCRIPTION_AND_COLORS);
        product.variants().stream().filter(Variant::active)
            .forEach(variant -> add(vector, variant.color().value(), DESCRIPTION_AND_COLORS));
        vectors.put(product.id(), vector);
    }

    private static void add(Map<String, Double> vector, String text, double weight) {
        terms(WORD.matcher(text).results().map(MatchResult::group).toList())
            .forEach(lexeme -> vector.merge(lexeme, weight, Math::max));
    }

    /** The sum of the best weight of each term; 0 when a term is missing. A prefix last term matches any start. */
    private double rank(String slug, List<String> terms, boolean lastIsPrefix) {
        if (terms.isEmpty()) {
            return 0;
        }
        Map<String, Double> vector = products.all().stream().filter(product -> product.slug().value().equals(slug))
            .findFirst().map(product -> vectors.getOrDefault(product.id(), Map.of())).orElse(Map.of());
        double total = 0;
        for (int i = 0; i < terms.size(); i++) {
            String term = terms.get(i);
            boolean prefix = lastIsPrefix && i == terms.size() - 1;
            double best = vector.entrySet().stream()
                .filter(entry -> prefix ? entry.getKey().startsWith(term) : entry.getKey().equals(term))
                .mapToDouble(Map.Entry::getValue).max().orElse(0);
            if (best == 0) {
                return 0;
            }
            total += best;
        }
        return total;
    }

    /** The prefix applies to the last word only when it is not a stop word (the database drops those). */
    private static boolean isPrefix(List<String> words) {
        return !STOP_WORDS.contains(normalize(words.getLast()));
    }

    private static List<String> terms(List<String> words) {
        return words.stream().map(InMemoryProductSearch::normalize).filter(word -> !STOP_WORDS.contains(word))
            .map(InMemoryProductSearch::stem).toList();
    }

    private static String normalize(String word) {
        return Normalizer.normalize(word, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static String stem(String word) {
        String stem = word.length() > 3 && word.endsWith("s") ? word.substring(0, word.length() - 1) : word;
        return stem.length() > 3 && "aeo".indexOf(stem.charAt(stem.length() - 1)) >= 0
            ? stem.substring(0, stem.length() - 1)
            : stem;
    }
}
