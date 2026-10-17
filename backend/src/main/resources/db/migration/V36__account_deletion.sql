-- T-E4-16: the customer deletes its account (UC-28). One transaction as the customer: it deletes its app_user row,
-- whose addresses, tokens, external identities and waiting link go with it (ON DELETE CASCADE, V29, V34, V35), and
-- the listeners of the same transaction unlink its orders (ordering, through unlink_deleted_customer_orders) and delete
-- its cart (cart, already allowed by cart_customer, V12).

-- RNF-27: a customer deletes its own user, never the administrator's.
CREATE POLICY app_user_customer_delete ON app_user FOR DELETE TO tienda_app
    USING (app_role() = 'CUSTOMER' AND id = app_user_id() AND role = 'CUSTOMER');

-- RNF-27: the deleted customer's orders keep their copy of the buyer's data for the shop's legal duties, without the
-- account. No policy lets a customer update its orders: once user_id is NULL the customer could not see the row, and
-- PostgreSQL checks an updated row against the SELECT policies. So this function, owned by tienda_owner (BYPASSRLS,
-- as lock_withdrawable_order of V26), unlinks them: only the orders of the customer of the transaction, and only once
-- its app_user row is gone in that transaction, which it checks seeing every user. Answers how many it unlinked.
CREATE FUNCTION unlink_deleted_customer_orders(p_customer UUID, p_at TIMESTAMP WITH TIME ZONE) RETURNS INTEGER
    LANGUAGE plpgsql VOLATILE SECURITY DEFINER
    SET search_path = public, pg_temp
    AS $$
DECLARE
    unlinked INTEGER;
BEGIN
    IF app_role() IS DISTINCT FROM 'CUSTOMER' OR p_customer IS DISTINCT FROM app_user_id()
        OR EXISTS (SELECT 1 FROM app_user u WHERE u.id = p_customer) THEN
        RAISE EXCEPTION 'a customer may only unlink its own orders, once its account is deleted'
            USING ERRCODE = 'insufficient_privilege';
    END IF;
    UPDATE orders SET user_id = NULL, unlinked_at = p_at, updated_at = p_at WHERE user_id = p_customer;
    GET DIAGNOSTICS unlinked = ROW_COUNT;
    RETURN unlinked;
END
$$;
REVOKE ALL ON FUNCTION unlink_deleted_customer_orders(UUID, TIMESTAMP WITH TIME ZONE) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION unlink_deleted_customer_orders(UUID, TIMESTAMP WITH TIME ZONE) TO tienda_app;

-- The guard of V24, where the customer's transaction may also unlink its own order (through the function above):
-- user_id goes from itself to NULL and unlinked_at from NULL to a value, nothing else but updated_at changes, and only
-- when its app_user row no longer exists. Otherwise a buyer still changes only the email, as in V24.
CREATE OR REPLACE FUNCTION orders_buyer_email_update_guard() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF app_role() = 'CUSTOMER' AND NEW.user_id IS DISTINCT FROM OLD.user_id THEN
        IF NOT (OLD.user_id = app_user_id() AND NEW.user_id IS NULL
                AND OLD.unlinked_at IS NULL AND NEW.unlinked_at IS NOT NULL
                AND NOT EXISTS (SELECT 1 FROM app_user u WHERE u.id = OLD.user_id)
                AND to_jsonb(NEW) - 'user_id' - 'unlinked_at' - 'updated_at'
                    IS NOT DISTINCT FROM to_jsonb(OLD) - 'user_id' - 'unlinked_at' - 'updated_at') THEN
            RAISE EXCEPTION 'a customer may only unlink its own orders, once its account is deleted'
                USING ERRCODE = 'insufficient_privilege';
        END IF;
        RETURN NEW;
    END IF;
    IF app_role() IN ('ANONYMOUS', 'CUSTOMER')
        AND to_jsonb(NEW) - 'customer_email' - 'updated_at'
            IS DISTINCT FROM to_jsonb(OLD) - 'customer_email' - 'updated_at' THEN
        RAISE EXCEPTION 'a buyer may only update the email of an order'
            USING ERRCODE = 'insufficient_privilege';
    END IF;
    RETURN NEW;
END
$$;
