package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.activity.api.ActivityAction;
import com.pijamaparty.store.activity.api.ActivityValues;
import com.pijamaparty.store.activity.api.FieldChange;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.Sku;
import com.pijamaparty.store.catalog.domain.Variant;
import com.pijamaparty.store.catalog.domain.VariantId;
import com.pijamaparty.store.catalog.domain.VariantIdentities;
import com.pijamaparty.store.catalog.domain.VariantMatrix;
import com.pijamaparty.store.platform.application.IdGenerator;
import com.pijamaparty.store.platform.domain.Role;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GenerateVariantMatrixService implements GenerateVariantMatrix {

    /** A random SKU clashes about once in a trillion; three tries make a failure practically impossible. */
    static final int SKU_ATTEMPTS = 3;

    private final LoadProducts loadProducts;
    private final SaveProduct saveProduct;
    private final OpenStockLevels openStockLevels;
    private final CatalogEvents events;
    private final CatalogActivity activity;
    private final VariantIdentities identities;
    private final Clock clock;

    GenerateVariantMatrixService(LoadProducts loadProducts, SaveProduct saveProduct, OpenStockLevels openStockLevels,
                                 CatalogEvents events, CatalogActivity activity, IdGenerator ids, SkuGenerator skus,
                                 Clock clock) {
        this.loadProducts = loadProducts;
        this.saveProduct = saveProduct;
        this.openStockLevels = openStockLevels;
        this.events = events;
        this.activity = activity;
        this.identities = new VariantIdentities() {
            @Override
            public VariantId nextId() {
                return VariantId.of(ids.newId());
            }

            @Override
            public Sku nextSku() {
                return skus.newSku();
            }
        };
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        VariantMatrix.Request request;
        switch (VariantMatrix.parse(command.sizes(), command.colors())) {
            case VariantMatrix.Parsed.Invalid invalid -> {
                return new Result.InvalidMatrix(invalid.problem());
            }
            case VariantMatrix.Parsed.Valid valid -> request = valid.request();
        }
        for (int attempt = 0; attempt < SKU_ATTEMPTS; attempt++) {
            // Loaded again on every attempt: a clash rolls back the whole write, new SKUs start from what is stored.
            Optional<Product> found = loadProducts.lockById(command.productId());
            if (found.isEmpty()) {
                return new Result.NotFound();
            }
            Product product = found.get();
            switch (product.generateVariants(request, identities, clock.instant())) {
                case Product.MatrixOutcome.Rejected rejected -> {
                    return new Result.InvalidMatrix(rejected.problem());
                }
                case Product.MatrixOutcome.Generated generated -> {
                    switch (saveProduct.update(product)) {
                        case SAVED -> {
                            openStockLevels.open(generated.created().stream().map(Variant::id).toList());
                            events.productChanged(product.id());
                            record(command, product, generated.created());
                            return new Result.Generated(ProductView.of(product), generated.created().size());
                        }
                        case SKU_ALREADY_USED -> {
                            // Try again with new SKUs.
                        }
                        case SLUG_ALREADY_USED, UNKNOWN_CATEGORY ->
                            throw new IllegalStateException("generating variants changes neither slug nor category");
                    }
                }
            }
        }
        throw new IllegalStateException("no unique SKUs after " + SKU_ATTEMPTS + " attempts");
    }

    /** UC-37: how many variants were created, and their sizes and colors; nothing when none was missing. */
    private void record(Command command, Product product, List<Variant> created) {
        if (created.isEmpty()) {
            return;
        }
        activity.record(CatalogEntries.product(command.actor(), ActivityAction.VARIANTS_GENERATED, product, List.of(
            FieldChange.of("variants", null, String.valueOf(created.size())),
            FieldChange.of("sizes", null, ActivityValues.list(created.stream()
                .sorted(Comparator.comparingInt(Variant::sizePosition))
                .map(variant -> variant.size().value()).distinct().toList())),
            FieldChange.of("colors", null, ActivityValues.list(created.stream()
                .sorted(Comparator.comparingInt(Variant::colorPosition))
                .map(variant -> variant.color().value()).distinct().toList())))));
    }
}
