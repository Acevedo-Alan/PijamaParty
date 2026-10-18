package com.pijamaparty.store.catalog.adapter.in.web;

import com.pijamaparty.store.catalog.application.SizeGuideView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** A category's size guide on the product page (RNF-31.5): each row has one value per column; blank values show "–". */
record SizeGuideResponse(List<String> columns, List<SizeGuideRowResponse> rows, @Schema(nullable = true) String note) {

    static SizeGuideResponse of(SizeGuideView guide) {
        return new SizeGuideResponse(guide.columns(), SizeGuideRowResponse.of(guide.rows()), guide.note().orElse(null));
    }

    record SizeGuideRowResponse(String size, List<String> values) {

        static List<SizeGuideRowResponse> of(List<SizeGuideView.RowView> rows) {
            return rows.stream().map(row -> new SizeGuideRowResponse(row.size(), row.values())).toList();
        }
    }
}
