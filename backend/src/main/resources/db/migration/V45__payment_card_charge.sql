-- T-E3-32 (UC-14): one card charge per order at a time. IN_PROGRESS while the provider has not answered a charge (or
-- timed out, and the store cannot know whether it charged: it expires after 10 minutes, decided 2026-11-06),
-- IN_REVIEW while the provider keeps the charge in review until the webhook says approved or rejected. Null when idle.
-- card_charge_key is the buyer's Idempotency-Key of that charge: the same submit repeated passes (the provider answers
-- it with the same payment), another submit does not.
ALTER TABLE payment
    ADD COLUMN card_charge_state      TEXT,
    ADD COLUMN card_charge_started_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN card_charge_key        UUID,
    ADD CONSTRAINT payment_card_charge_state_ck CHECK (card_charge_state IN ('IN_PROGRESS', 'IN_REVIEW')),
    ADD CONSTRAINT payment_card_charge_columns_ck CHECK (
        (card_charge_state IS NULL) = (card_charge_started_at IS NULL)
        AND (card_charge_state IS NULL) = (card_charge_key IS NULL));
