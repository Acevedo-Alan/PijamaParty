-- Payments module (T-E3-07, UC-15): the order number of each payment. MercadoPago's notification leads to a payment
-- whose external_reference is the order number (T-E3-06), and payments does not read the orders table
-- (ARCHITECTURE.md section 3, rule 4), so the payment keeps the number it was created with. No payment exists outside
-- development yet (the store is published in E6), so the column is required: a development database with payments
-- must be recreated.

ALTER TABLE payment ADD COLUMN order_number BIGINT NOT NULL;

ALTER TABLE payment ADD CONSTRAINT payment_order_number_ck CHECK (order_number > 0);
-- One payment per order and provider (payment_order_provider_uk), and order numbers are unique (RNF-20.6).
ALTER TABLE payment ADD CONSTRAINT payment_provider_order_number_uk UNIQUE (provider, order_number);
