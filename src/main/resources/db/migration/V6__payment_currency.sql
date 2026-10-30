-- Every stored amount carries its ISO 4217 currency (MASTER_PROMPT section 9). Existing rows are
-- test data from before multi-currency cards; pending flows take their card's currency, the rest
-- are assumed AZN (payment rows keep only the last four digits, so the card cannot be found).
ALTER TABLE public.payment_onboarding ADD COLUMN currency character varying(255);
UPDATE public.payment_onboarding p SET currency = c.currency FROM public.cards c WHERE c.id = p.from_card_id;
UPDATE public.payment_onboarding SET currency = 'AZN' WHERE currency IS NULL;
ALTER TABLE public.payment_onboarding
    ALTER COLUMN currency SET NOT NULL,
    ADD CONSTRAINT payment_onboarding_currency_check CHECK (currency IN ('AZN', 'USD', 'EUR'));

ALTER TABLE public.payment ADD COLUMN currency character varying(255);
UPDATE public.payment SET currency = 'AZN';
ALTER TABLE public.payment
    ALTER COLUMN currency SET NOT NULL,
    ADD CONSTRAINT payment_currency_check CHECK (currency IN ('AZN', 'USD', 'EUR'));
