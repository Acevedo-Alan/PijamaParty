-- UC-46 (T-E8-30): the items of the store setup guide the owner chose to omit, kept on the server so the choice holds
-- on every device. One row per item: the store has one administrator (D-E8-01). Only the items the guide lets omit can
-- be stored; the application decides that too (SetupItem), the check backs it. No personal data, so no Row Level
-- Security (RNF-27).
CREATE TABLE setup_guide_omission (
    item       TEXT                     NOT NULL,
    omitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT setup_guide_omission_pk PRIMARY KEY (item),
    CONSTRAINT setup_guide_omission_item_ck CHECK (item IN ('INSTAGRAM', 'HOME_BANNER'))
);
