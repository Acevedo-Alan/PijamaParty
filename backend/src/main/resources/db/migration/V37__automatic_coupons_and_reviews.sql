-- T-E5-02: automatic coupons (UC-23, UC-11) and product reviews (UC-24, UC-25).

-- UC-11: a coupon is created as automatic or as a code coupon; every existing coupon is a code coupon. The default
-- keeps today's writers unchanged.
ALTER TABLE coupon ADD COLUMN automatic BOOLEAN NOT NULL DEFAULT FALSE;

-- UC-11: whether a coupon is automatic never changes, so its scope rows never belong to a code coupon.
CREATE FUNCTION coupon_automatic_update_guard() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.automatic IS DISTINCT FROM OLD.automatic THEN
        RAISE EXCEPTION 'whether a coupon is automatic is fixed when it is created'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER coupon_automatic_update_guard
    BEFORE UPDATE ON coupon
    FOR EACH ROW EXECUTE FUNCTION coupon_automatic_update_guard();

-- UC-23: the optional scope of an automatic coupon, one row per product or category. target_id refers to the catalog
-- module and has no foreign key (ARCHITECTURE.md section 3, rule 6): a target the catalog no longer has matches no
-- line. The limit of 100 targets belongs to the coupon in the domain. No Row Level Security, as coupon (V13).
CREATE TABLE coupon_scope (
    coupon_id   UUID NOT NULL,
    target_type TEXT NOT NULL,
    target_id   UUID NOT NULL,
    CONSTRAINT coupon_scope_pk PRIMARY KEY (coupon_id, target_type, target_id),
    CONSTRAINT coupon_scope_coupon_fk FOREIGN KEY (coupon_id) REFERENCES coupon (id) ON DELETE CASCADE,
    CONSTRAINT coupon_scope_target_type_ck CHECK (target_type IN ('PRODUCT', 'CATEGORY'))
);

-- Only an automatic coupon has a scope: the domain enforces it, this backs it.
CREATE FUNCTION coupon_scope_automatic_guard() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM coupon c WHERE c.id = NEW.coupon_id AND c.automatic) THEN
        RAISE EXCEPTION 'only an automatic coupon has a scope'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER coupon_scope_automatic_guard
    BEFORE INSERT OR UPDATE ON coupon_scope
    FOR EACH ROW EXECUTE FUNCTION coupon_scope_automatic_guard();

-- UC-24, UC-25: one review per customer and product. product_id refers to the catalog module and user_id to the
-- accounts module, without foreign keys (ARCHITECTURE.md section 3, rule 6): the reviews module deletes a customer's
-- reviews when its account is deleted (UC-28). The average is computed when the product page is read, over the
-- approved ones (product_status_idx).
CREATE TABLE review (
    id           UUID                     NOT NULL,
    product_id   UUID                     NOT NULL,
    user_id      UUID                     NOT NULL,
    rating       SMALLINT                 NOT NULL,
    -- Optional, trimmed: blank is stored as NULL.
    text         TEXT,
    -- The first word of the account's name when the review was last submitted.
    author_name  TEXT                     NOT NULL,
    status       TEXT                     NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    -- The last submission: editing a review sends it back to PENDING.
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    moderated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT review_pk PRIMARY KEY (id),
    CONSTRAINT review_user_product_uk UNIQUE (user_id, product_id),
    CONSTRAINT review_rating_ck CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT review_text_ck CHECK (text = btrim(text) AND char_length(text) BETWEEN 1 AND 1000),
    CONSTRAINT review_author_name_ck CHECK (
        author_name = btrim(author_name) AND char_length(author_name) BETWEEN 1 AND 100),
    CONSTRAINT review_status_ck CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT review_updated_at_ck CHECK (updated_at >= created_at),
    -- Null while pending, set by the administrator's decision.
    CONSTRAINT review_moderated_at_ck CHECK ((status = 'PENDING') = (moderated_at IS NULL))
);

-- UC-24: the approved reviews of a product page. UC-25: the moderation tabs, by the last submission.
CREATE INDEX review_product_status_idx ON review (product_id, status);
CREATE INDEX review_status_updated_at_idx ON review (status, updated_at);

ALTER TABLE review ENABLE ROW LEVEL SECURITY;
ALTER TABLE review FORCE ROW LEVEL SECURITY;

-- RNF-27: everyone reads the approved reviews of the product page; a customer also reads all of its own.
CREATE POLICY review_public_select ON review FOR SELECT TO tienda_app
    USING (app_role() IN ('ANONYMOUS', 'CUSTOMER') AND status = 'APPROVED');
CREATE POLICY review_customer_select ON review FOR SELECT TO tienda_app
    USING (app_role() = 'CUSTOMER' AND user_id = app_user_id());

-- RNF-27: a customer submits its own reviews, always pending moderation, and edits them only back to pending (the
-- columns it may change are guarded below).
CREATE POLICY review_customer_insert ON review FOR INSERT TO tienda_app
    WITH CHECK (app_role() = 'CUSTOMER' AND user_id = app_user_id() AND status = 'PENDING');
CREATE POLICY review_customer_update ON review FOR UPDATE TO tienda_app
    USING (app_role() = 'CUSTOMER' AND user_id = app_user_id())
    WITH CHECK (app_role() = 'CUSTOMER' AND user_id = app_user_id() AND status = 'PENDING');

-- RNF-27, UC-28: a customer deletes its own reviews only once its account is gone in the same transaction (the
-- listener of CustomerAccountDeleted runs after its app_user row is deleted). The customer sees only its own user
-- (app_user_customer_select, V5), so its row missing means it was deleted.
CREATE POLICY review_customer_delete ON review FOR DELETE TO tienda_app
    USING (app_role() = 'CUSTOMER' AND user_id = app_user_id()
           AND NOT EXISTS (SELECT 1 FROM app_user u WHERE u.id = review.user_id));

-- RNF-27, UC-25: the administrator reads every review, approves or rejects it, and deletes it.
CREATE POLICY review_admin_select ON review FOR SELECT TO tienda_app
    USING (app_role() = 'ADMIN');
CREATE POLICY review_admin_update ON review FOR UPDATE TO tienda_app
    USING (app_role() = 'ADMIN')
    WITH CHECK (app_role() = 'ADMIN' AND status IN ('APPROVED', 'REJECTED'));
CREATE POLICY review_admin_delete ON review FOR DELETE TO tienda_app
    USING (app_role() = 'ADMIN');

-- Policies choose rows, not columns: a customer changes only its rating, text, author name and submission time (and
-- the review goes back to pending, so its moderation time is cleared); the administrator only the status and the
-- moderation time.
CREATE FUNCTION review_update_guard() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF app_role() = 'CUSTOMER'
        AND to_jsonb(NEW) - 'rating' - 'text' - 'author_name' - 'updated_at' - 'status' - 'moderated_at'
            IS DISTINCT FROM to_jsonb(OLD) - 'rating' - 'text' - 'author_name' - 'updated_at' - 'status'
                - 'moderated_at' THEN
        RAISE EXCEPTION 'a customer may only update the rating, text and author name of its review'
            USING ERRCODE = 'insufficient_privilege';
    END IF;
    IF app_role() = 'ADMIN'
        AND to_jsonb(NEW) - 'status' - 'moderated_at' IS DISTINCT FROM to_jsonb(OLD) - 'status' - 'moderated_at' THEN
        RAISE EXCEPTION 'the administrator may only update the status of a review'
            USING ERRCODE = 'insufficient_privilege';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER review_update_guard
    BEFORE UPDATE ON review
    FOR EACH ROW EXECUTE FUNCTION review_update_guard();
