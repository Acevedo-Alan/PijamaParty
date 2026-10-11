-- T-E1-17, UC-26: full text search over products with the spanish configuration, ignoring accents.

-- Trusted extension: the schema owner (tienda_owner, owner of the database) may create it without being a superuser.
-- Its functions belong to the bootstrap superuser, so they keep the default EXECUTE for PUBLIC.
CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA public;

-- Kept up to date by the application (T-E1-18): unaccent is not immutable, so this cannot be a generated column.
-- A product not indexed yet matches nothing.
ALTER TABLE product ADD COLUMN search_vector TSVECTOR NOT NULL DEFAULT ''::tsvector;

CREATE INDEX product_search_vector_idx ON product USING GIN (search_vector);

-- The one place that weighs the searchable texts: name (A), category and material (B), description and the colors of
-- the active variants (C). The listener of T-E1-18 runs update product set search_vector = product_search_vector(id).
CREATE FUNCTION product_search_vector(p_product_id UUID) RETURNS TSVECTOR
    LANGUAGE sql
    STABLE
    SET search_path = pg_catalog, public
AS $$
SELECT setweight(to_tsvector('spanish', unaccent(p.name)), 'A')
    || setweight(to_tsvector('spanish', unaccent(c.name || ' ' || p.material)), 'B')
    || setweight(to_tsvector('spanish', unaccent(p.description || ' ' || coalesce(colors.list, ''))), 'C')
FROM product p
JOIN category c ON c.id = p.category_id
LEFT JOIN LATERAL (
    SELECT string_agg(DISTINCT v.color, ' ') AS list
    FROM variant v
    WHERE v.product_id = p.id AND v.active
) colors ON true
WHERE p.id = p_product_id
$$;

-- deploy/db/init/01-roles.sh revokes EXECUTE from PUBLIC on the functions of tienda_owner.
GRANT EXECUTE ON FUNCTION product_search_vector(UUID) TO tienda_app;

UPDATE product SET search_vector = product_search_vector(id);
