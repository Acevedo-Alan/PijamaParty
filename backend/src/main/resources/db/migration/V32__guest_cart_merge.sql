-- T-E4-04: the guest cart joins the customer's at sign-in (UC-19). The sign-in runs as the customer carrying the cart
-- token of the browser, so the customer reaches the guest cart of that token, and only it: it reads it with its items
-- and deletes it, and the items go with it through the foreign key. It never changes the guest cart nor its items:
-- its lines move to the customer's own cart (RNF-27).
CREATE POLICY cart_customer_guest_select ON cart FOR SELECT TO tienda_app
    USING (app_role() = 'CUSTOMER' AND token_hash = app_cart_token_hash());
CREATE POLICY cart_customer_guest_delete ON cart FOR DELETE TO tienda_app
    USING (app_role() = 'CUSTOMER' AND token_hash = app_cart_token_hash());

-- V12 let items follow every cart the actor reads, which now includes that guest cart. Reads still do; writes follow
-- only the actor's own cart: the customer's by its user id, the guest's by its token.
CREATE FUNCTION app_owns_cart(target_cart UUID) RETURNS BOOLEAN
    LANGUAGE sql STABLE PARALLEL SAFE
    AS $$
        SELECT EXISTS (SELECT 1 FROM cart c
                       WHERE c.id = target_cart
                         AND ((app_role() = 'CUSTOMER' AND c.user_id = app_user_id())
                              OR (app_role() = 'ANONYMOUS' AND c.token_hash = app_cart_token_hash())))
    $$;
GRANT EXECUTE ON FUNCTION app_owns_cart(UUID) TO tienda_app;

DROP POLICY cart_item_owner ON cart_item;
CREATE POLICY cart_item_select ON cart_item FOR SELECT TO tienda_app
    USING (app_role() IN ('CUSTOMER', 'ANONYMOUS') AND EXISTS (SELECT 1 FROM cart c WHERE c.id = cart_id));
CREATE POLICY cart_item_owner_insert ON cart_item FOR INSERT TO tienda_app
    WITH CHECK (app_owns_cart(cart_id));
CREATE POLICY cart_item_owner_update ON cart_item FOR UPDATE TO tienda_app
    USING (app_owns_cart(cart_id))
    WITH CHECK (app_owns_cart(cart_id));
CREATE POLICY cart_item_owner_delete ON cart_item FOR DELETE TO tienda_app
    USING (app_owns_cart(cart_id));
