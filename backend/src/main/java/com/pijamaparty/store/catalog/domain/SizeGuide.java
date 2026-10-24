package com.pijamaparty.store.catalog.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The size guide of a {@link Category} (RNF-31.5): the measurement columns the administrator named, one row per size
 * with one value per column, both in the administrator's order, and an optional note on how to measure. It is
 * replaced whole; {@link SizeGuideForm} builds it from what the administrator typed.
 */
public record SizeGuide(CategoryId categoryId, List<String> columns, List<Row> rows, Optional<String> note,
                        Instant updatedAt) {

    public static final int MAX_COLUMNS = 6;
    public static final int MAX_COLUMN_NAME = 30;
    public static final int MAX_ROWS = 20;
    public static final int MAX_VALUE = 20;
    public static final int MAX_NOTE = 500;

    public SizeGuide {
        Objects.requireNonNull(categoryId, "categoryId");
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
        Objects.requireNonNull(note, "note");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (!areValidColumns(columns)) {
            throw new IllegalArgumentException("not valid size guide columns");
        }
        if (!areValidRows(rows, columns.size())) {
            throw new IllegalArgumentException("not valid size guide rows");
        }
        if (note.isPresent() && !isValidNote(note.get())) {
            throw new IllegalArgumentException("not a valid size guide note");
        }
    }

    /** Whether both guides say the same, whenever each was saved (UC-37: saving it unchanged changes nothing). */
    public boolean sameContentAs(SizeGuide other) {
        return columns.equals(other.columns) && rows.equals(other.rows) && note.equals(other.note);
    }

    /** 1 to 6 names, each trimmed, one line, 1 to 30 characters, unique ignoring case. */
    static boolean areValidColumns(List<String> columns) {
        return !columns.isEmpty() && columns.size() <= MAX_COLUMNS
            && columns.stream().allMatch(name -> name != null && PlainText.isSingleLine(name, MAX_COLUMN_NAME))
            && areUniqueIgnoringCase(columns);
    }

    /** 1 to 20 rows of unique sizes (ignoring case), each with one value per column. */
    static boolean areValidRows(List<Row> rows, int columns) {
        return !rows.isEmpty() && rows.size() <= MAX_ROWS
            && rows.stream().allMatch(row -> row.values().size() == columns)
            && areUniqueIgnoringCase(rows.stream().map(row -> row.size().value()).toList());
    }

    /** Trimmed, 1 to 500 characters, line breaks allowed. */
    static boolean isValidNote(String note) {
        return !note.isEmpty() && PlainText.isMultiLine(note, MAX_NOTE);
    }

    /** Trimmed, one line, at most 20 characters; empty is allowed (shown as a dash). */
    static boolean isValidValue(String value) {
        return value != null && (value.isEmpty() || PlainText.isSingleLine(value, MAX_VALUE));
    }

    private static boolean areUniqueIgnoringCase(List<String> texts) {
        var seen = new HashSet<String>();
        return texts.stream().allMatch(text -> seen.add(text.toLowerCase(Locale.ROOT)));
    }

    /** One size and its value for each column, in the columns' order. */
    public record Row(Size size, List<String> values) {

        public Row {
            Objects.requireNonNull(size, "size");
            values = List.copyOf(values);
            if (!values.stream().allMatch(SizeGuide::isValidValue)) {
                throw new IllegalArgumentException("not valid size guide values");
            }
        }
    }
}
