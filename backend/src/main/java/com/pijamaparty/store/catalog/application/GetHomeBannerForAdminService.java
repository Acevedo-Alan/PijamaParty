package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.platform.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetHomeBannerForAdminService implements GetHomeBannerForAdmin {

    private final LoadHomeBanner loadHomeBanner;

    GetHomeBannerForAdminService(LoadHomeBanner loadHomeBanner) {
        this.loadHomeBanner = loadHomeBanner;
    }

    @Override
    @Transactional(readOnly = true)
    public Result execute(Query query) {
        if (!query.actor().is(Role.ADMIN)) {
            return new Result.Forbidden();
        }
        return new Result.Found(HomeBannerView.of(loadHomeBanner.load()));
    }
}
