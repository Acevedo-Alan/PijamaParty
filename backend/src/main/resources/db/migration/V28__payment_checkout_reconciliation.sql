-- Payments module (T-E3b-03, UC-15): every minute the reconciliation reads the checkouts that expire within a recent
-- window. Only checkouts with an expiry (MODO's payment requests) are ever due, so the index leaves MercadoPago's out.
-- payment_checkout_provider_checkout_uk (V27) already finds a payment by its checkout.

CREATE INDEX payment_checkout_expires_idx ON payment_checkout (expires_at) WHERE expires_at IS NOT NULL;
