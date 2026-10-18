package com.pijamaparty.store.catalog.application;

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
        Optional<Product> listed = Slug.parse(query.slug())
            .flatMap(products::findBySlug)
            .filter(product -> categories.findById(product.details().categoryId())
                .map(product::isListedIn)
                .orElse(false));
        if (listed.isEmpty()) {
            return new Result.NotFound();
        }
        Product product = listed.get();
        var stock = availability.availableOf(product.sellableVariants().stream().map(Variant::id).toList());
        var sizeGuide = sizeGuides.findByCategory(product.details().categoryId()).map(SizeGuideView::of);
        return new Result.Found(ProductDetail.of(product, stock, sizeGuide));
    }
}
