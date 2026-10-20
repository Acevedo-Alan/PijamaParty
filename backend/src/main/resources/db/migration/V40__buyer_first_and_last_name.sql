-- T-E7-10: the buyer's first and last name are kept apart (D-13, docs/use-cases/E7-storefront-design.md, changes to
-- UC-13). The store has no real orders yet, so existing rows split at the first space; a single word goes to the
-- first name, with '-' as the last name. Both stay personal data (R-05): never logged.
ALTER TABLE orders
    ADD COLUMN customer_first_name TEXT,
    ADD COLUMN customer_last_name  TEXT;

-- Run as tienda_owner (BYPASSRLS, and app_role() is NULL, so the guards of V20, V33 and V36 let it through).
UPDATE orders
SET customer_first_name = btrim(left(split_part(customer_name, ' ', 1), 50)),
    customer_last_name  = COALESCE(
        NULLIF(btrim(left(btrim(substr(customer_name, char_length(split_part(customer_name, ' ', 1)) + 2)), 50)), ''),
        '-');

ALTER TABLE orders
    ALTER COLUMN customer_first_name SET NOT NULL,
    ALTER COLUMN customer_last_name SET NOT NULL,
    ADD CONSTRAINT orders_customer_first_name_ck CHECK (
        customer_first_name = btrim(customer_first_name) AND char_length(customer_first_name) BETWEEN 1 AND 50),
    ADD CONSTRAINT orders_customer_last_name_ck CHECK (
        customer_last_name = btrim(customer_last_name) AND char_length(customer_last_name) BETWEEN 1 AND 50);

-- No generated full name: a BEFORE trigger sees it NULL in NEW, so the to_jsonb comparison of
-- orders_buyer_email_update_guard (V36) would refuse every email correction. The queries join both instead.

-- The guard of V33 with the two columns in place of customer_name.
CREATE OR REPLACE FUNCTION orders_status_update_guard() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF app_role() IN ('ADMIN', 'SYSTEM') THEN
        IF (NEW.id, NEW.number, NEW.tracking_token_hash, NEW.customer_first_name, NEW.customer_last_name,
            NEW.customer_email, NEW.customer_phone, NEW.national_id, NEW.street, NEW.street_number, NEW.floor,
            NEW.apartment, NEW.city, NEW.province, NEW.postal_code, NEW.subtotal_cents, NEW.discount_cents,
            NEW.shipping_cents, NEW.total_cents, NEW.coupon_id, NEW.coupon_code, NEW.payment_provider,
            NEW.terms_version, NEW.reservation_expires_at, NEW.created_at, NEW.unlinked_at)
            IS DISTINCT FROM (OLD.id, OLD.number, OLD.tracking_token_hash, OLD.customer_first_name,
             OLD.customer_last_name, OLD.customer_email, OLD.customer_phone, OLD.national_id, OLD.street,
             OLD.street_number, OLD.floor, OLD.apartment, OLD.city, OLD.province, OLD.postal_code, OLD.subtotal_cents,
             OLD.discount_cents, OLD.shipping_cents, OLD.total_cents, OLD.coupon_id, OLD.coupon_code,
             OLD.payment_provider, OLD.terms_version, OLD.reservation_expires_at, OLD.created_at, OLD.unlinked_at) THEN
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

ALTER TABLE orders
    DROP CONSTRAINT orders_customer_name_ck,
    DROP COLUMN customer_name;
