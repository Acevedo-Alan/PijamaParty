-- Readers of the Row Level Security context set by RlsTransactionManager at the start of every transaction
-- (RNF-27). Policies of later stages use them instead of current_setting directly, so an unset or empty value is
-- always NULL and never a cast error. Without context every function returns NULL: policies match no rows.
--
--   CREATE POLICY order_customer ON orders FOR SELECT TO tienda_app
--       USING (app_role() = 'CUSTOMER' AND user_id = app_user_id());

CREATE FUNCTION app_role() RETURNS TEXT
    LANGUAGE sql STABLE PARALLEL SAFE
    AS $$ SELECT nullif(current_setting('app.role', true), '') $$;

CREATE FUNCTION app_user_id() RETURNS UUID
    LANGUAGE sql STABLE PARALLEL SAFE
    AS $$ SELECT nullif(current_setting('app.user_id', true), '')::uuid $$;

CREATE FUNCTION app_cart_token_hash() RETURNS TEXT
    LANGUAGE sql STABLE PARALLEL SAFE
    AS $$ SELECT nullif(current_setting('app.cart_token_hash', true), '') $$;

CREATE FUNCTION app_tracking_token_hash() RETURNS TEXT
    LANGUAGE sql STABLE PARALLEL SAFE
    AS $$ SELECT nullif(current_setting('app.tracking_token_hash', true), '') $$;

-- Functions are not executable by PUBLIC (deploy/db/init/01-roles.sh): grant only what the application needs.
GRANT EXECUTE ON FUNCTION app_role(), app_user_id(), app_cart_token_hash(), app_tracking_token_hash() TO tienda_app;
