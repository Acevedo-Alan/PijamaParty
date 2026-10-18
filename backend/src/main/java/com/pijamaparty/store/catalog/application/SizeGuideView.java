package com.pijamaparty.store.catalog.application;

import com.pijamaparty.store.catalog.domain.SizeGuide;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** A category's size guide as the back office and the product page show it (RNF-31.5). */
public record SizeGuideView(List<String> columns, List<RowView> rows, Optional<String> note, Instant updatedAt) {

    public SizeGuideView {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
    }

    static SizeGuideView of(SizeGuide guide) {
        return new SizeGuideView(guide.columns(),
            guide.rows().stream().map(row -> new RowView(row.size().value(), row.values())).toList(),
            guide.note(), guide.updatedAt());
    }

    /** One size and its value for each column. */
    public record RowView(String size, List<String> values) {

        public RowView {
            values = List.copyOf(values);
        }
    }
}
