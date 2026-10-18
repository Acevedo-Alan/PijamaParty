package com.pijamaparty.store.catalog.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * RNF-31.5: a size guide as the administrator typed it, before its rules apply. Every text is trimmed; a blank note is
 * none. Null lists or texts count as invalid, not as empty.
 */
public record SizeGuideForm(List<String> columns, List<RowForm> rows, String note) {

    /** The guide of the category, or every field that breaks a rule. */
    public Outcome toGuide(CategoryId categoryId, Instant now) {
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(now, "now");
        Set<Field> invalid = EnumSet.noneOf(Field.class);
        Optional<List<String>> trimmedColumns = trimmedAll(columns).filter(SizeGuide::areValidColumns);
        if (trimmedColumns.isEmpty()) {
            invalid.add(Field.COLUMNS);
        }
        int columnCount = columns == null ? 0 : columns.size();
        Optional<List<SizeGuide.Row>> parsedRows = parsedRows().filter(all -> SizeGuide.areValidRows(all, columnCount));
        if (parsedRows.isEmpty()) {
            invalid.add(Field.ROWS);
        }
        Optional<String> trimmedNote = Optional.ofNullable(note).map(String::strip).filter(text -> !text.isEmpty());
        if (trimmedNote.isPresent() && !SizeGuide.isValidNote(trimmedNote.get())) {
            invalid.add(Field.NOTE);
        }
        if (!invalid.isEmpty()) {
            return new Outcome.Invalid(invalid);
        }
        return new Outcome.Valid(new SizeGuide(categoryId, trimmedColumns.get(), parsedRows.get(), trimmedNote, now));
    }

    private Optional<List<SizeGuide.Row>> parsedRows() {
        if (rows == null) {
            return Optional.empty();
        }
        var parsed = new ArrayList<SizeGuide.Row>();
        for (RowForm row : rows) {
            if (row == null) {
                return Optional.empty();
            }
            Optional<Size> size = Size.parse(row.size());
            Optional<List<String>> values = trimmedAll(row.values())
                .filter(all -> all.stream().allMatch(SizeGuide::isValidValue));
            if (size.isEmpty() || values.isEmpty()) {
                return Optional.empty();
            }
            parsed.add(new SizeGuide.Row(size.get(), values.get()));
        }
        return Optional.of(parsed);
    }

    /** Every text trimmed; empty when the list or one of its texts is missing. */
    private static Optional<List<String>> trimmedAll(List<String> texts) {
        if (texts == null || texts.stream().anyMatch(Objects::isNull)) {
            return Optional.empty();
        }
        return Optional.of(texts.stream().map(String::strip).toList());
    }

    /** A row as typed: the size and one value per column. */
    public record RowForm(String size, List<String> values) {}

    public enum Field {
        COLUMNS,
        ROWS,
        NOTE
    }

    public sealed interface Outcome {

        record Valid(SizeGuide guide) implements Outcome {}

        record Invalid(Set<Field> fields) implements Outcome {

            public Invalid {
                fields = Set.copyOf(fields);
            }
        }
    }
}
