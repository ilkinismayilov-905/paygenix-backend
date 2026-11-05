-- Every stored amount carries its ISO 4217 currency (MASTER_PROMPT section 9, T-126). Older transfer rows keep
-- only last-four digits, so their card (and currency) cannot be found: they stay NULL.
ALTER TABLE public.transfer ADD COLUMN currency character varying(255);
ALTER TABLE public.transfer ADD CONSTRAINT transfer_currency_check CHECK (currency IN ('AZN', 'USD', 'EUR'));
