package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.ContentHash;
import com.pijamaparty.store.catalog.domain.HomeBanner;
import com.pijamaparty.store.catalog.domain.HomeBannerPhoto;
import com.pijamaparty.store.catalog.domain.ImageFormat;
import com.pijamaparty.store.catalog.domain.StorageKey;
import com.pijamaparty.store.platform.application.IdGenerator;
import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;

/**
 * Not transactional on purpose, as {@code UploadProductImageService}: the conversion and the files happen between two
 * short transactions. The old photo's files are removed only after the new photo is committed; the new files are
 * removed when the photo is not saved.
 */
@Service
class ReplaceHomeBannerPhotoService implements ReplaceHomeBannerPhoto {

    private final HomeBannerPhotoTransactions transactions;
    private final ImageProcessor processor;
    private final ImageStore imageStore;
    private final IdGenerator ids;

    ReplaceHomeBannerPhotoService(HomeBannerPhotoTransactions transactions, ImageProcessor processor,
                                  ImageStore imageStore, IdGenerator ids) {
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
        ContentHash contentHash = ContentHash.of(command.content());
        HomeBannerPhotoTransactions.Step check = transactions.check(command.uploadKey(), contentHash);
        switch (check.outcome()) {
            case HomeBanner.PhotoUpload.AlreadyUploaded uploaded -> {
                return new Result.AlreadyUploaded(check.banner());
            }
            case HomeBanner.PhotoUpload.KeyReused reused -> {
                return new Result.KeyReused();
            }
            case HomeBanner.PhotoUpload.Ready ready -> {
                // Convert below.
            }
            case HomeBanner.PhotoUpload.Replaced replaced ->
                throw new IllegalStateException("a checked photo is not replaced");
        }
        return switch (processor.process(command.content())) {
            case ImageProcessor.Result.TooLarge tooLarge -> new Result.TooLarge();
            case ImageProcessor.Result.Unreadable unreadable -> new Result.Unreadable();
            case ImageProcessor.Result.Processed processed -> {
                StorageKey storageKey = StorageKey.of(ids.newId());
                imageStore.save(storageKey, processed.renditions());
                yield replace(new HomeBannerPhoto(storageKey, processed.placeholder(), command.uploadKey(),
                    contentHash));
            }
        };
    }

    private Result replace(HomeBannerPhoto photo) {
        HomeBannerPhotoTransactions.Step replacement;
        try {
            replacement = transactions.replace(photo);
        } catch (RuntimeException e) {
            imageStore.delete(photo.storageKey());
            throw e;
        }
        return switch (replacement.outcome()) {
            case HomeBanner.PhotoUpload.Replaced replaced -> {
                replaced.previous().ifPresent(previous -> imageStore.delete(previous.storageKey()));
                yield new Result.Replaced(replacement.banner());
            }
            // Another upload with the same key won meanwhile.
            case HomeBanner.PhotoUpload.AlreadyUploaded uploaded -> {
                imageStore.delete(photo.storageKey());
                yield new Result.AlreadyUploaded(replacement.banner());
            }
            case HomeBanner.PhotoUpload.KeyReused reused -> {
                imageStore.delete(photo.storageKey());
                yield new Result.KeyReused();
            }
            case HomeBanner.PhotoUpload.Ready ready -> throw new IllegalStateException("a replacement is never ready");
        };
    }
}
