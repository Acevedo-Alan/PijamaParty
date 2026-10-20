package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Category;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.Slug;
import com.pijamaparty.store.catalog.domain.Variant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetProductDetailService implements GetProductDetail {

    private final LoadCategories categories;
    private final LoadProducts products;
    private final VariantAvailabilityLookup availability;
    private final LoadSizeGuides sizeGuides;

    GetProductDetailService(LoadCategories categories, LoadProducts products,
                            VariantAvailabilityLookup availability, LoadSizeGuides sizeGuides) {
        this.categories = categories;
        this.products = products;
        this.availability = availability;
        this.sizeGuides = sizeGuides;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        Optional<Product> found = Slug.parse(query.slug()).flatMap(products::findBySlug);
        Optional<Category> category = found.flatMap(product -> categories.findById(product.details().categoryId()));
        if (found.isEmpty() || category.isEmpty() || !found.get().isListedIn(category.get())) {
            return new Result.NotFound();
        }
        Product product = found.get();
        var stock = availability.availableOf(product.sellableVariants().stream().map(Variant::id).toList());
        var sizeGuide = sizeGuides.findByCategory(product.details().categoryId()).map(SizeGuideView::of);
        return new Result.Found(ProductDetail.of(product, category.get(), stock, sizeGuide));
    }
}
