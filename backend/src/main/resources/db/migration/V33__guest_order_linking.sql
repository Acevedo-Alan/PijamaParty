-- T-E4-08: guest orders join the customer's account (UC-22). At every sign-in of a customer whose email is verified,
-- the system links the orders without a user whose buyer email is the account's. An order unlinked by an account
-- deletion (UC-28, T-E4-16) is never linked again: it keeps the instant of the unlinking.
ALTER TABLE orders
    ADD COLUMN unlinked_at TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT orders_unlinked_ck CHECK (unlinked_at IS NULL OR user_id IS NULL);

-- The guest orders a sign-in may link, by their buyer email.
CREATE INDEX orders_guest_email_idx ON orders (customer_email) WHERE user_id IS NULL AND unlinked_at IS NULL;

-- The email of a customer whose email is verified, or NULL. It reads app_user (accounts module) under the caller's
-- policies: the system reads every user; any other actor gets NULL for every user but, at most, itself.
CREATE FUNCTION app_verified_customer_email(customer UUID) RETURNS TEXT
    LANGUAGE sql STABLE PARALLEL SAFE
    AS $$
        SELECT u.email FROM app_user u WHERE u.id = customer AND u.role = 'CUSTOMER' AND u.email_verified
    $$;
GRANT EXECUTE ON FUNCTION app_verified_customer_email(UUID) TO tienda_app;

-- The guard of V14, where the system may also link a guest order: user_id goes from NULL to a customer whose
-- verified email is the order's, on an order never unlinked, and nothing else but updated_at changes. Nobody changes
-- unlinked_at yet: the account deletion of T-E4-16 brings its own rule. Everything else is as in V14.
CREATE OR REPLACE FUNCTION orders_status_update_guard() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF app_role() IN ('ADMIN', 'SYSTEM') THEN
        IF (NEW.id, NEW.number, NEW.tracking_token_hash, NEW.customer_name, NEW.customer_email,
            NEW.customer_phone, NEW.national_id, NEW.street, NEW.street_number, NEW.floor, NEW.apartment, NEW.city,
            NEW.province, NEW.postal_code, NEW.subtotal_cents, NEW.discount_cents, NEW.shipping_cents,
            NEW.total_cents, NEW.coupon_id, NEW.coupon_code, NEW.payment_provider, NEW.terms_version,
            NEW.reservation_expires_at, NEW.created_at, NEW.unlinked_at)
            IS DISTINCT FROM (OLD.id, OLD.number, OLD.tracking_token_hash, OLD.customer_name,
             OLD.customer_email, OLD.customer_phone, OLD.national_id, OLD.street, OLD.street_number, OLD.floor,
             OLD.apartment, OLD.city, OLD.province, OLD.postal_code, OLD.subtotal_cents, OLD.discount_cents,
             OLD.shipping_cents, OLD.total_cents, OLD.coupon_id, OLD.coupon_code, OLD.payment_provider,
             OLD.terms_version, OLD.reservation_expires_at, OLD.created_at, OLD.unlinked_at) THEN
            RAISE EXCEPTION 'the administrator and the system may only update the status, the shipment tracking number and the delivery date of an order'
                USING ERRCODE = 'insufficient_privilege';
        END IF;
        IF NEW.user_id IS DISTINCT FROM OLD.user_id
            AND NOT (app_role() = 'SYSTEM' AND OLD.user_id IS NULL AND OLD.unlinked_at IS NULL
                     AND NEW.user_id IS NOT NULL
                     AND app_verified_customer_email(NEW.user_id) IS NOT DISTINCT FROM OLD.customer_email
                     AND (NEW.status, NEW.shipment_tracking_number, NEW.delivered_at, NEW.paid_after_closing_at)
                         IS NOT DISTINCT FROM
                         (OLD.status, OLD.shipment_tracking_number, OLD.delivered_at, OLD.paid_after_closing_at))
            THEN
            RAISE EXCEPTION 'only the system links a guest order, to the verified customer of its email, and changes nothing else'
                USING ERRCODE = 'insufficient_privilege';
        END IF;
    END IF;
    RETURN NEW;
END
$$;
