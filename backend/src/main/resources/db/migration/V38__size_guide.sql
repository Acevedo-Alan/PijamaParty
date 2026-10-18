-- T-E5-11: the size guide of a category (RNF-31.5, docs/use-cases/E5-promotions-and-reviews.md). Catalog data without
-- personal data: no Row Level Security (RNF-27). The domain holds the rules; these checks back them.

-- Whether every text of the array (of any dimension) is trimmed and between the given lengths.
CREATE FUNCTION size_guide_texts_valid(texts TEXT[], min_length INTEGER, max_length INTEGER) RETURNS BOOLEAN
    LANGUAGE sql
    IMMUTABLE
    AS $$
SELECT coalesce(bool_and(t IS NOT NULL AND t = btrim(t) AND char_length(t) BETWEEN min_length AND max_length), TRUE)
FROM unnest(texts) AS t
$$;

-- Functions are not executable by PUBLIC (deploy/db/init/01-roles.sh); the checks run as the writing role.
GRANT EXECUTE ON FUNCTION size_guide_texts_valid(TEXT[], INTEGER, INTEGER) TO tienda_app;

-- At most one guide per category, replaced whole and deleted with its category. cells holds one row per size and one
-- value per column; PostgreSQL keeps a two-dimensional array rectangular.
CREATE TABLE size_guide (
    category_id  UUID                     NOT NULL,
    column_names TEXT[]                   NOT NULL,
    sizes        TEXT[]                   NOT NULL,
    cells        TEXT[]                   NOT NULL,
    note         TEXT,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT size_guide_pk PRIMARY KEY (category_id),
    CONSTRAINT size_guide_category_fk FOREIGN KEY (category_id) REFERENCES category (id) ON DELETE CASCADE,
    CONSTRAINT size_guide_column_names_ck CHECK (array_ndims(column_names) = 1
        AND cardinality(column_names) BETWEEN 1 AND 6 AND size_guide_texts_valid(column_names, 1, 30)),
    CONSTRAINT size_guide_sizes_ck CHECK (array_ndims(sizes) = 1
        AND cardinality(sizes) BETWEEN 1 AND 20 AND size_guide_texts_valid(sizes, 1, 20)),
    CONSTRAINT size_guide_cells_ck CHECK (array_ndims(cells) = 2
        AND array_length(cells, 1) = cardinality(sizes) AND array_length(cells, 2) = cardinality(column_names)
        AND size_guide_texts_valid(cells, 0, 20)),
    CONSTRAINT size_guide_note_ck CHECK (note = btrim(note) AND char_length(note) BETWEEN 1 AND 500)
);
