package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.Color;
import com.pijamaparty.store.catalog.domain.ContentHash;
import com.pijamaparty.store.catalog.domain.ImageFormat;
import com.pijamaparty.store.catalog.domain.ImageId;
import com.pijamaparty.store.catalog.domain.Product;
import com.pijamaparty.store.catalog.domain.StorageKey;
import com.pijamaparty.store.platform.application.IdGenerator;
import com.pijamaparty.store.platform.domain.Role;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Not transactional on purpose: the conversion and the files happen between two short transactions (UC-06). The first
 * one refuses what is known to fail before converting; the second one locks the product, checks again and adds the
 * photo. Files whose photo is not added are removed.
 */
@Service
class UploadProductImageService implements UploadProductImage {

    private final ProductImageTransactions transactions;
    private final ImageProcessor processor;
    private final ImageStore imageStore;
    private final IdGenerator ids;

    UploadProductImageService(ProductImageTransactions transactions, ImageProcessor processor, ImageStore imageStore,
                              IdGenerator ids) {
        this.transactions = transactions;
        this.processor = processor;
        this.imageStore = imageStore;
        this.ids = ids;
    }

    @Override
    public Result execute(Command command) {
        if (!command.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        if (ImageFormat.detect(command.content()).isEmpty()) {
            return new Result.UnsupportedFormat();
        }
        Optional<Color> color = command.color().flatMap(Color::parse);
        if (command.color().isPresent() && color.isEmpty()) {
            // Text that cannot be a color is no color of the product either.
            return new Result.UnknownColor();
        }
        ContentHash contentHash = ContentHash.of(command.content());
        Optional<Product.ImageUpload> check =
            transactions.check(command.productId(), command.uploadKey(), contentHash, color);
        if (check.isEmpty()) {
            return new Result.NotFound();
        }
        if (!(check.get() instanceof Product.ImageUpload.Ready)) {
            return resultOf(check.get());
        }
        return switch (processor.process(command.content())) {
            case ImageProcessor.Result.TooLarge tooLarge -> new Result.TooLarge();
            case ImageProcessor.Result.Unreadable unreadable -> new Result.Unreadable();
            case ImageProcessor.Result.Processed processed -> {
                StorageKey storageKey = StorageKey.of(ids.newId());
                imageStore.save(storageKey, processed.renditions());
                var image = new Product.NewImage(ImageId.of(ids.newId()), color, storageKey, processed.placeholder(),
                    command.uploadKey(), contentHash);
                yield add(command, image);
            }
        };
    }

    private Result add(Command command, Product.NewImage image) {
        Optional<Product.ImageUpload> outcome;
        try {
            outcome = transactions.add(command.actor(), command.productId(), image);
        } catch (RuntimeException e) {
            imageStore.delete(image.storageKey());
            throw e;
        }
        if (outcome.isEmpty() || !(outcome.get() instanceof Product.ImageUpload.Added)) {
            // Another upload with the same key won, or the product filled up meanwhile.
            imageStore.delete(image.storageKey());
        }
        return outcome.map(UploadProductImageService::resultOf).orElseGet(Result.NotFound::new);
    }

    private static Result resultOf(Product.ImageUpload upload) {
        return switch (upload) {
            case Product.ImageUpload.Added added -> new Result.Uploaded(ProductView.ImageView.of(added.image()));
            case Product.ImageUpload.AlreadyUploaded uploaded ->
                new Result.AlreadyUploaded(ProductView.ImageView.of(uploaded.image()));
            case Product.ImageUpload.KeyReused reused -> new Result.KeyReused();
            case Product.ImageUpload.TooManyImages tooMany -> new Result.TooManyImages();
            case Product.ImageUpload.UnknownColor unknown -> new Result.UnknownColor();
            case Product.ImageUpload.Ready ready -> throw new IllegalStateException("a checked upload is not a result");
        };
    }
}
