-- Shipping module (T-E3-01, UC-12): a shipping cost per province and the free-shipping threshold. No Row Level
-- Security: no personal data, and the tables are outside the RNF-27 scope list.

CREATE TABLE shipping_zone_rate (
    -- UC-12: the 24 jurisdictions of Argentina, as the Province enum. A province without a row has no shipping.
    province   TEXT                     NOT NULL,
    -- Integer cents ARS (R-01); 0 is free shipping to that province. 1,000,000,000 is the price maximum of UC-06.
    cost_cents BIGINT                   NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT shipping_zone_rate_pk PRIMARY KEY (province),
    CONSTRAINT shipping_zone_rate_province_ck CHECK (province IN (
        'BUENOS_AIRES', 'CIUDAD_DE_BUENOS_AIRES', 'CATAMARCA', 'CHACO', 'CHUBUT', 'CORDOBA', 'CORRIENTES',
        'ENTRE_RIOS', 'FORMOSA', 'JUJUY', 'LA_PAMPA', 'LA_RIOJA', 'MENDOZA', 'MISIONES', 'NEUQUEN', 'RIO_NEGRO',
        'SALTA', 'SAN_JUAN', 'SAN_LUIS', 'SANTA_CRUZ', 'SANTA_FE', 'SANTIAGO_DEL_ESTERO', 'TIERRA_DEL_FUEGO',
        'TUCUMAN')),
    CONSTRAINT shipping_zone_rate_cost_ck CHECK (cost_cents BETWEEN 0 AND 1000000000)
);

-- The shop's settings: a single row, created here, that the application only updates.
CREATE TABLE shop_setting (
    id                            BOOLEAN                  NOT NULL DEFAULT TRUE,
    -- UC-12: shipping is free when the subtotal after the discount reaches it. Null: no free shipping.
    free_shipping_threshold_cents BIGINT,
    updated_at                    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT shop_setting_pk PRIMARY KEY (id),
    CONSTRAINT shop_setting_single_row_ck CHECK (id),
    CONSTRAINT shop_setting_free_shipping_threshold_ck CHECK (
        free_shipping_threshold_cents BETWEEN 1 AND 1000000000)
);

INSERT INTO shop_setting (id, free_shipping_threshold_cents, updated_at) VALUES (TRUE, NULL, now());

REVOKE INSERT, DELETE, TRUNCATE ON TABLE shop_setting FROM tienda_app;
